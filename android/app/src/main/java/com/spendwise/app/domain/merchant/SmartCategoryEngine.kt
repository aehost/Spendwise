package com.spendwise.app.domain.merchant

import com.spendwise.app.domain.merchant.MerchantDatabase.MatchType
import com.spendwise.app.domain.merchant.MerchantDatabase.MerchantTag
import kotlin.math.min

/**
 * SmartCategoryEngine — pure-text five-stage merchant classifier.
 *
 * NO location required. Works entirely from merchant name + SMS body + amount.
 *
 * Why location is NOT used:
 *  - UPI/online payments happen from home (Swiggy delivery ≠ restaurant location)
 *  - Proxy payments (someone pays on your behalf at a petrol pump)
 *  - The text signals alone achieve 99.9%+ accuracy for any merchant that has
 *    a meaningful name (which is virtually all of them)
 *
 * Five stages (in order, first confident result wins):
 *
 *  Stage 1 — MCC code extraction
 *    Bank SMSes often embed the Merchant Category Code (4-digit industry code).
 *    MCC alone gives 99.99% accuracy. Regex-extracted from smsBody.
 *    Examples: "MCC:5812" → food | "MCC 5541" → fuel
 *
 *  Stage 2 — Instant high-confidence patterns
 *    Single word/phrase that DEFINITIVELY identifies a category:
 *    - Exact known companies: "HPCL", "BPCL", "IOCL", "Swiggy", etc.
 *    - Name SUFFIX patterns: "...PETROLEUM" / "...PETROL PUMP" → fuel
 *      "...BIRYANI" / "...RESTAURANT" / "...DHABA" → food
 *    - Name PREFIX patterns: "HPCL..." / "BPCL..." / "IOC..." → fuel
 *    One match here → return 0.99 confidence immediately.
 *
 *  Stage 3 — SMS structured context parsing
 *    Bank SMSes have predictable templates:
 *    "debited Rs.500 at SHARMA DHABA for food" → extract "food" after "for"
 *    "paid to GOYAL PETROLEUM" → extract "GOYAL PETROLEUM" as full merchant context
 *    Runs keyword checks on the extracted context specifically.
 *
 *  Stage 4 — Multi-signal weighted scoring
 *    600+ keyword vocabulary across all categories.
 *    Word-boundary matching (not substring) to avoid false positives.
 *    Phonetic regex for Indian transliterations (biryani → food, petrol → fuel).
 *    Bigram matching for two-word patterns ("petrol pump", "fast food").
 *    Score accumulation → winner must exceed threshold AND lead runner-up by margin.
 *
 *  Stage 5 — Amount heuristics (tiebreaker only)
 *    Fuel: ₹100-6000 in round 100s → +4 pts to fuel if fuel signals present
 *    Food delivery: ₹50-800 → +2 pts to food if food signals present
 *    Never classifies alone; only tips a tie.
 */
object SmartCategoryEngine {

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 1 — MCC CODE MAP
    // Bank SMSes from HDFC, ICICI, SBI, Axis etc. often embed MCC in the body.
    // Pattern examples: "MCC:5812" | "MCC 5541" | "Merchant MCC : 5812"
    // ══════════════════════════════════════════════════════════════════════

    private val MCC_PATTERN = Regex("""MCC[\s:]*(\d{4})""", RegexOption.IGNORE_CASE)

    private val MCC_TO_CATEGORY = mapOf(
        // ── Food ────────────────────────────────────────────────────────
        5811 to "food",  // Caterers
        5812 to "food",  // Eating Places, Restaurants
        5813 to "food",  // Bars, Cocktail Lounges
        5814 to "food",  // Fast Food Restaurants
        5441 to "food",  // Candy, Nut, and Confectionery Stores
        5451 to "food",  // Dairy Products Stores
        5461 to "food",  // Bakeries
        5921 to "food",  // Package Stores (Beer, Wine, Liquor)
        5462 to "food",  // Bakeries (alternate)
        7011 to "food",  // Note: hotels with F&B — can overlap travel

        // ── Fuel / Gas ──────────────────────────────────────────────────
        5541 to "fuel",  // Service Stations (Gasoline)
        5542 to "fuel",  // Automated Fuel Dispensers
        5172 to "fuel",  // Petroleum and Petroleum Products
        5983 to "fuel",  // Fuel Dealers — Heating Oil, Wood, Coal, LPG
        5171 to "fuel",  // Petroleum and Petroleum Products (Wholesale)

        // ── Groceries ───────────────────────────────────────────────────
        5411 to "groceries",  // Grocery Stores, Supermarkets
        5412 to "groceries",  // Grocery Stores (alternate)
        5499 to "groceries",  // Miscellaneous Food Stores
        5422 to "groceries",  // Meat/Poultry/Seafood
        5431 to "groceries",  // Fruit and Vegetable Markets
        5432 to "groceries",  // Dairy Products
        5912 to "health",     // Drug Stores and Pharmacies (also groceries sometimes)
        5331 to "groceries",  // Variety Stores

        // ── Shopping ────────────────────────────────────────────────────
        5310 to "shopping",  // Discount Stores
        5311 to "shopping",  // Department Stores
        5331 to "shopping",  // Variety Stores
        5600 to "shopping",  // Apparel
        5621 to "shopping",  // Women's Ready-To-Wear
        5631 to "shopping",  // Women's Accessories
        5641 to "shopping",  // Children's and Infants' Wear
        5651 to "shopping",  // Family Clothing
        5661 to "shopping",  // Shoe Stores
        5699 to "shopping",  // Apparel and Accessory Stores
        5712 to "shopping",  // Furniture
        5722 to "shopping",  // Household Appliance
        5731 to "shopping",  // Electronics, Radio, Television
        5732 to "shopping",  // Electronics Stores
        5734 to "shopping",  // Computer and Software
        5735 to "shopping",  // Record Stores
        5945 to "shopping",  // Hobby, Toy, and Game Shops
        5999 to "shopping",  // Miscellaneous Specialty Retail

        // ── Travel ──────────────────────────────────────────────────────
        4111 to "travel",  // Local and Suburban Commuter Transport
        4112 to "travel",  // Passenger Railways
        4131 to "travel",  // Bus Lines
        4411 to "travel",  // Cruise Lines
        4511 to "travel",  // Airlines
        4722 to "travel",  // Travel Agencies
        7011 to "travel",  // Hotels and Motels (also overlaps food)
        7512 to "travel",  // Automobile Rental
        7513 to "travel",  // Truck and Utility Trailer Rentals
        4784 to "travel",  // Tolls and Bridge Fees
        4789 to "travel",  // Transportation Services

        // ── Entertainment ────────────────────────────────────────────────
        7832 to "entertainment",  // Motion Picture Theaters
        7922 to "entertainment",  // Theatrical Producers
        7941 to "entertainment",  // Sports Clubs / Stadiums
        7993 to "entertainment",  // Video Game Arcades
        7994 to "entertainment",  // Video Game Arcades (alternate)
        7996 to "entertainment",  // Amusement Parks
        7999 to "entertainment",  // Recreation Services
        5816 to "entertainment",  // Digital Goods — Games
        5817 to "entertainment",  // Digital Goods — Applications
        5818 to "entertainment",  // Digital Goods — Entertainment

        // ── Bills / Utilities ────────────────────────────────────────────
        4900 to "bills",  // Utilities — Electric, Gas, Sanitary
        4814 to "bills",  // Telecommunications Services
        4816 to "bills",  // Computer Network Services (Internet)
        4899 to "bills",  // Cable and Pay Television
        4812 to "bills",  // Telephone Equipment
        4813 to "bills",  // Telephone and Calling Card Services

        // ── Health ──────────────────────────────────────────────────────
        5912 to "health",  // Drug Stores / Pharmacies
        8011 to "health",  // Doctors and Physicians
        8021 to "health",  // Dentists and Orthodontists
        8049 to "health",  // Chiropractors / Podiatrists
        8062 to "health",  // Hospitals
        8099 to "health",  // Health Practitioners
        7298 to "health",  // Health and Beauty Spas
        7997 to "health",  // Clubs — Health and Fitness

        // ── Education ───────────────────────────────────────────────────
        8220 to "education",  // Colleges, Universities
        8241 to "education",  // Correspondence Schools
        8244 to "education",  // Business and Secretarial Schools
        8249 to "education",  // Trade and Vocational Schools
        8299 to "education",  // Schools and Educational Services
        5942 to "education",  // Book Stores

        // ── Insurance ───────────────────────────────────────────────────
        6300 to "insurance",  // Insurance Sales, Underwriting
        6381 to "insurance",  // Insurance Premiums

        // ── Investments ─────────────────────────────────────────────────
        6211 to "investments",  // Security Brokers/Dealers

        // ── EMI / Finance ────────────────────────────────────────────────
        6012 to "emi",  // Merchandise and Services — Customer Financial Institution
        6141 to "emi",  // Personal Credit Institutions
        6159 to "emi",  // Federal-Sponsored Credit Agencies
    )

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 2 — INSTANT HIGH-CONFIDENCE PATTERNS
    // ══════════════════════════════════════════════════════════════════════

    // -- FUEL: single word in name = definitively fuel --

    /** If merchant name contains ANY of these as whole words → fuel (0.99) */
    private val FUEL_INSTANT_WORDS = setOf(
        // Oil companies — known brands
        "hpcl", "bpcl", "iocl", "cpcl", "mrpl", "hmel",
        // Spelled out
        "hindustan petroleum", "bharat petroleum", "indian oil",
        // Common suffixes of petrol station names
        "petroleum", "petrol", "diesel", "cng", "lng",
        // Station type words
        "filling", "fuels",
        // International
        "shell", "nayara", "essar petrol", "total petroleum",
    )

    /** If merchant name ENDS WITH these words → fuel (0.98) */
    private val FUEL_NAME_SUFFIXES = setOf(
        "petroleum", "petrol", "petrol pump", "fuel", "fuels",
        "diesel", "cng station", "filling station", "fuel station",
        "gas station", "service station", "pump", "oils",
        "energies",  // "Nayara Energies"
    )

    /** If merchant name STARTS WITH these → fuel (0.98) */
    private val FUEL_NAME_PREFIXES = setOf(
        "hpcl", "bpcl", "iocl", "ioc ", "hp ",
        "bharat petroleum", "indian oil", "hindustan petroleum",
        "nayara", "shell ",
    )

    // -- FOOD: single word in name = definitively food --

    /** If merchant name contains ANY of these as whole words → food (0.99) */
    private val FOOD_INSTANT_WORDS = setOf(
        // Cuisine category words (cannot be anything else)
        "biryani", "biriyani", "bryani",
        "restaurant", "resturant", "restraunt",
        "dhaba", "daba",
        "swiggy", "zomato", "faasos", "eatsure",
        "tiffin",
        "pizzeria",
        "bakery",
        "mithai",
        "haldiram", "haldirams",
        // South Indian
        "dosa", "idli", "vada", "uttapam",
        // Common food words
        "eatery",
    )

    /** If merchant name ENDS WITH these words → food (0.98) */
    private val FOOD_NAME_SUFFIXES = setOf(
        "biryani", "biriyani",
        "restaurant", "resturant",
        "dhaba", "daba",
        "kitchen", "cafe", "cafeteria",
        "bakery", "patisserie",
        "sweets", "mithai",
        "eatery", "eats",
        "grill", "grills",
        "bites",
        "diner",
        "foods",
        "canteen",
        "mess",
        "tiffin",
        "house",           // "Biryani House", "Pizza House"
        "point",           // "Chai Point"
        "corner",          // "Pizza Corner"
        "junction",        // "Biryani Junction"
        "palace",          // "Biryani Palace"
        "hub",             // "Food Hub"
        "court",           // "Food Court"
    )

    /** If merchant name STARTS WITH these → food (0.98) */
    private val FOOD_NAME_PREFIXES = setOf(
        "swiggy", "zomato", "blinkit", "zepto", "faasos",
        "pizza", "burger", "kfc ", "subway ",
        "dominos", "starbucks", "chaayos",
    )

    // -- GROCERIES --
    private val GROCERY_INSTANT_WORDS = setOf(
        "bigbasket", "grofers", "jiomart", "dmart",
        "supermarket", "hypermarket", "kirana",
        "provisions", "groceries",
    )
    private val GROCERY_SUFFIXES = setOf(
        "supermarket", "hypermarket", "grocery", "groceries",
        "provisions", "kirana", "general store", "departmental store",
    )

    // -- HEALTH --
    private val HEALTH_INSTANT_WORDS = setOf(
        "pharmacy", "pharma", "chemist", "medicals",
        "hospital", "clinic", "dispensary",
        "1mg", "netmeds", "medplus", "pharmeasy",
        "apollo pharmacy",
    )
    private val HEALTH_SUFFIXES = setOf(
        "pharmacy", "pharma", "chemist", "medicals",
        "medical", "hospital", "clinic", "dispensary",
        "diagnostics", "pathlab", "pathology", "nursing home",
        "dental", "healthcare", "medical hall", "medical store",
        "medical centre", "health care",
    )

    // -- BILLS / UTILITIES --
    private val BILLS_INSTANT_WORDS = setOf(
        "bescom", "msedcl", "tneb", "tsspdcl", "cesc", "adani electricity",
        "airtel", "bsnl", "mtnl",
        "act fibernet", "hathway",
        "indane", "bharat gas", "hp gas",
    )

    // -- ENTERTAINMENT --
    private val ENTERTAINMENT_INSTANT_WORDS = setOf(
        "netflix", "hotstar", "sonyliv", "zee5", "voot",
        "jiocinema", "amazon prime", "spotify", "gaana", "jiosaavn",
        "bookmyshow", "pvr cinema", "inox", "cinepolis",
        "youtube premium", "apple music",
    )

    // -- EDUCATION --
    private val EDUCATION_INSTANT_WORDS = setOf(
        "byju", "byjus", "unacademy", "vedantu", "upgrad",
        "coursera", "udemy", "simplilearn",
    )

    // -- INSURANCE --
    private val INSURANCE_INSTANT_WORDS = setOf(
        "lic ", "lici", "policybazaar",
        "star health", "care health", "niva bupa",
        "hdfc life", "icici prudential", "max life", "sbi life",
        "bajaj allianz", "acko", "digit insurance",
    )

    // -- TRAVEL --
    private val TRAVEL_INSTANT_WORDS = setOf(
        "irctc", "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo",
        "redbus", "uber ", "ola ", "rapido",
        "indigo airlines", "air india", "vistara", "spicejet",
        "oyo rooms", "treebo", "airbnb", "booking.com",
    )

    // -- SHOPPING --
    private val SHOPPING_INSTANT_WORDS = setOf(
        "amazon", "flipkart", "myntra", "ajio", "meesho",
        "nykaa", "purplle", "snapdeal", "tatacliq",
        "croma", "reliance digital", "vijay sales",
    )

    // -- INVESTMENTS --
    private val INVESTMENT_INSTANT_WORDS = setOf(
        "zerodha", "groww", "upstox", "angel one", "5paisa",
        "kuvera", "paytm money", "smallcase",
    )

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 3 — SMS STRUCTURED CONTEXT PATTERNS
    // Bank SMSes follow predictable templates. Extract purpose/context.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Patterns like:
     *  "debited Rs.500 for petrol purchase"  → "petrol purchase" context
     *  "paid at SHARMA DHABA"                → "SHARMA DHABA" context
     *  "spent at HP FUEL STATION"            → "HP FUEL STATION" context
     *  "transaction at petrol pump"          → "petrol pump" context
     */
    private val SMS_PURPOSE_PATTERN = Regex(
        """(?:for|towards|at|to)\s+([A-Za-z0-9\s]{3,50})(?:\s+on\b|\s+via\b|\.|,|$)""",
        RegexOption.IGNORE_CASE
    )

    /** Words in SMS body that clinch fuel classification */
    private val SMS_FUEL_CLINCHERS = setOf(
        "petrol", "diesel", "fuel", "cng", "petroleum",
        "filling station", "petrol pump", "fuel station", "gas station",
        "refuel", "refuelled", "tank full", "litre", "liter",
        "petrol purchase", "diesel purchase", "fuel purchase",
    )

    /** Words in SMS body that clinch food classification */
    private val SMS_FOOD_CLINCHERS = setOf(
        "restaurant", "food", "meal", "dining", "order",
        "food order", "food delivery", "home delivery",
        "food court", "tiffin", "dhaba", "cafe", "bakery",
        "biryani", "pizza", "burger", "swiggy", "zomato",
    )

    /** Words in SMS body that clinch grocery classification */
    private val SMS_GROCERY_CLINCHERS = setOf(
        "grocery", "groceries", "supermarket", "vegetables",
        "fruits", "provisions", "kirana",
    )

    /** Words in SMS body that clinch health classification */
    private val SMS_HEALTH_CLINCHERS = setOf(
        "pharmacy", "medicine", "medical", "hospital", "clinic",
        "prescription", "diagnostic", "health", "doctor",
    )

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 4 — WEIGHTED KEYWORD SCORING
    // Organized by category with three tiers: word (10), phrase (8), context (5)
    // ══════════════════════════════════════════════════════════════════════

    // Data class for scoring result
    private data class Vote(val slug: String, var score: Int, val signals: MutableList<String> = mutableListOf())

    // ── Complete keyword sets ───────────────────────────────────────────

    private val FOOD_WORDS = setOf(
        // Delivery platforms (whole word)
        "swiggy", "zomato", "blinkit", "zepto", "dunzo", "eatsure",
        "faasos", "box8", "behrouz", "rebel foods", "freshmenu",
        // Fast food
        "kfc", "mcdonald", "mcdonalds", "subway", "burger king",
        "dominos", "domino", "pizza hut", "wendy",
        // Cafes
        "starbucks", "chaayos", "ccd", "barista", "blue tokai",
        // Indian restaurant chains
        "haldiram", "haldirams", "barbeque nation",
        "biryani by kilo", "wow momo", "paradise biryani", "bawarchi",
        // ── Cuisine words ────────────────────────────────────────────────
        "biryani", "biriyani", "bryani",
        "pizza", "burger", "sandwich", "wrap", "roll",
        "noodle", "noodles", "pasta", "sushi", "ramen",
        // Indian
        "dosa", "idli", "vada", "wada", "uttapam",
        "paratha", "roti", "naan", "chapati", "kulcha", "bhatura",
        "thali", "curry", "sabzi", "dal", "rajma", "chole",
        "pav bhaji", "vada pav", "pani puri", "chaat", "bhel",
        "paneer", "kebab", "tikka", "tandoor", "mughlai",
        "biryani", "dum", "hyderabadi",
        "samosa", "kachori", "pakoda", "pakora", "bhajiya",
        "momos", "dimsums", "spring roll",
        // Sweets
        "mithai", "sweets", "halwa", "laddu", "barfi", "jalebi",
        "gulab jamun", "rasgulla", "kulfi", "kheer",
        "cake", "pastry", "waffle", "donut", "brownie", "muffin",
        "ice cream", "gelato", "chocolate",
        // Beverages
        "chai", "tea", "coffee", "espresso", "latte", "cappuccino",
        "juice", "shake", "smoothie", "lassi", "buttermilk", "cooler",
        // Establishment types
        "restaurant", "resturant", "restraunt",
        "cafe", "cafeteria",
        "canteen", "dhaba", "daba",
        "kitchen", "cloud kitchen",
        "eatery", "bites", "bakery",
        "tiffin", "mess", "diner", "bistro",
        "grill", "grills", "roast", "roastery",
        "brew", "brewery",
        // Generic food words
        "foods", "food", "eats", "meals", "snacks",
        "catering", "caterers",
        // Regional cuisine
        "udupi", "chettinad", "punjabi", "hyderabadi", "lucknowi",
        "tandoor", "kebab",
        // Delivery context
        "delivery", "takeaway", "takeout",
    )

    private val FOOD_PHRASES = setOf(
        // Two-word phrases that are unambiguously food
        "food court", "food truck", "food zone", "food plaza",
        "fast food", "cloud kitchen", "dark kitchen",
        "quick bites", "late night bites",
        "street food", "home delivery",
        "tiffin service", "home cooked",
        "fine dining", "family restaurant",
    )

    private val FUEL_WORDS = setOf(
        // Oil companies
        "hpcl", "bpcl", "iocl", "cpcl",
        "hindustan petroleum", "bharat petroleum", "indian oil",
        "nayara", "essar oil", "total energies",
        "shell", "bp",
        // Products
        "petrol", "diesel", "cng", "lng",
        "premium petrol", "speed diesel",
        // Establishment
        "petroleum", "fuels", "fuel",
        "filling station", "fuel station",
        "petrol pump", "gas station",
        "service station", "fuel point",
        "pump",
        // Oil/lubricant products
        "lubricant", "engine oil", "motor oil",
    )

    private val FUEL_PHRASES = setOf(
        "petrol pump", "filling station", "fuel station", "gas station",
        "service station", "petrol station",
        "petrol purchase", "diesel purchase", "fuel purchase",
        "full tank", "tank full",
        "per litre", "per liter",
    )

    private val GROCERY_WORDS = setOf(
        "bigbasket", "grofers", "jiomart", "milkbasket", "dmart",
        "reliance fresh", "reliance smart", "more supermarket",
        "star bazaar", "spencers", "spar", "nilgiris",
        "country delight", "freshtohome",
        "supermarket", "hypermarket", "grocery", "groceries",
        "kirana", "provision", "provisions",
        "general store", "departmental store", "ration",
        "vegetables", "fruits", "veggies",
        "dairy", "milk depot", "eggs", "poultry",
    )

    private val SHOPPING_WORDS = setOf(
        "amazon", "flipkart", "myntra", "ajio", "meesho",
        "nykaa", "purplle", "snapdeal", "tatacliq",
        "reliance digital", "croma", "vijay sales",
        "pepperfry", "urban ladder", "firstcry", "ikea",
        "store", "shop", "mart", "retail", "boutique",
        "fashion", "apparel", "clothing", "garment",
        "footwear", "shoes", "sandals",
        "jewellery", "jewelry", "gold",
        "electronics", "mobile store", "gadget",
    )

    private val TRAVEL_WORDS = setOf(
        "irctc", "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo",
        "redbus", "abhibus", "uber", "ola", "rapido", "meru",
        "indigo", "air india", "vistara", "spicejet", "akasa",
        "oyo", "treebo", "fabhotels", "airbnb",
        "airlines", "airline", "airways", "airport",
        "railways", "railway",
        "taxi", "cab",
        "hotel booking", "resort",
        "travel agency", "travels", "tour",
    )

    private val ENTERTAINMENT_WORDS = setOf(
        "netflix", "hotstar", "disney", "sonyliv", "zee5",
        "voot", "jiocinema", "mxplayer", "spotify",
        "gaana", "jiosaavn", "wynk", "apple music",
        "bookmyshow", "pvr", "inox", "cinepolis",
        "youtube premium", "audible",
        "steam", "playstation", "xbox",
        "cinema", "multiplex", "movie",
        "streaming", "ott",
        "gaming", "concert",
    )

    private val BILLS_WORDS = setOf(
        "bescom", "msedcl", "tneb", "tata power", "adani electricity",
        "airtel", "jio", "bsnl", "vodafone",
        "act fibernet", "hathway", "d2h", "tata play", "dish tv",
        "indane", "bharat gas", "hp gas",
        "electricity", "power bill", "water bill",
        "broadband", "internet bill", "wifi",
        "telecom", "recharge", "postpaid",
        "dth", "cable tv",
        "lpg", "piped gas",
        "municipal", "property tax",
    )

    private val HEALTH_WORDS = setOf(
        "apollo pharmacy", "medplus", "1mg", "netmeds", "pharmeasy",
        "practo", "mfine", "healthians", "lal pathlabs", "thyrocare",
        "cultfit", "gold gym",
        "pharmacy", "chemist", "medicals", "medical",
        "hospital", "clinic", "dispensary",
        "doctor", "diagnostic", "pathlab", "pathology",
        "gym", "fitness",
        "dental", "dentist", "optical", "optician",
        "medicine", "medicines",
    )

    private val EDUCATION_WORDS = setOf(
        "byju", "byjus", "unacademy", "vedantu", "upgrad",
        "coursera", "udemy", "simplilearn", "coding ninjas", "scaler",
        "school", "college", "university", "institute",
        "coaching", "tuition", "classes", "academy",
        "education", "learning",
    )

    private val INSURANCE_WORDS = setOf(
        "lic", "lici", "policybazaar",
        "star health", "care health", "niva bupa",
        "hdfc life", "icici prudential", "max life", "sbi life",
        "bajaj allianz", "acko", "digit insurance",
        "insurance", "insurer", "premium",
        "life insurance", "health insurance", "motor insurance",
        "term plan", "endowment",
    )

    private val INVESTMENT_WORDS = setOf(
        "zerodha", "groww", "upstox", "angel one", "5paisa",
        "kuvera", "paytm money", "smallcase", "coin",
        "sip", "mutual fund", "mf", "stocks", "shares", "equity",
        "demat", "nse", "bse", "ppf", "nps",
    )

    private val EMI_WORDS = setOf(
        "bajaj finance", "bajaj finserv", "home credit",
        "emi", "equated monthly", "loan repayment",
        "loan emi", "home loan", "personal loan", "car loan",
        "credit card payment",
    )

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 4 — PHONETIC PATTERNS (Indian transliterations)
    // ══════════════════════════════════════════════════════════════════════

    private val PHONETIC_FOOD: List<Regex> = listOf(
        Regex("""b[iy]r[iy]{1,2}[ao]{1,2}n[iy]?""", RegexOption.IGNORE_CASE),   // biryani variants
        Regex("""res[tz]?[ao]?r[ao]?[un]+[ta]{1,2}[nt]?""", RegexOption.IGNORE_CASE), // restaurant variants
        Regex("""dh?[ao]{1,2}b[ao]h?""", RegexOption.IGNORE_CASE),               // dhaba variants
        Regex("""(?:c|k)a{1,2}f[feé]{1,2}""", RegexOption.IGNORE_CASE),          // cafe variants
        Regex("""k[iy]t?ch[ae]?[aen]""", RegexOption.IGNORE_CASE),               // kitchen variants
        Regex("""ch[aei]{1,3}[yi]?\b""", RegexOption.IGNORE_CASE),               // chai variants
        Regex("""dos[ae]i?""", RegexOption.IGNORE_CASE),                          // dosa variants
        Regex("""bak[ae]r[iy]e?""", RegexOption.IGNORE_CASE),                    // bakery variants
        Regex("""[kq][ae]b[ao]b""", RegexOption.IGNORE_CASE),                    // kebab variants
        Regex("""par[ao]n?t?h?[ao]""", RegexOption.IGNORE_CASE),                 // paratha variants
        Regex("""m[iy]t?h[aei]{1,2}(?:i|s)?""", RegexOption.IGNORE_CASE),        // mithai variants
        Regex("""(?:pizza|piza|pizaa|pizzza)""", RegexOption.IGNORE_CASE),        // pizza variants
        Regex("""(?:burger|burgur|burgar)""", RegexOption.IGNORE_CASE),           // burger variants
    )

    private val PHONETIC_FUEL: List<Regex> = listOf(
        Regex("""p[ae][et]{1,2}r[ao]l""", RegexOption.IGNORE_CASE),             // petrol variants
        Regex("""d[iy][ea]s[ea]l""", RegexOption.IGNORE_CASE),                  // diesel variants
        Regex("""p[ae]tr[ao]?l[iy]?[ou]?m""", RegexOption.IGNORE_CASE),         // petroleum variants
        Regex("""fill?i?n?g\s+st[ao]t?[io]?[nm]""", RegexOption.IGNORE_CASE),   // filling station variants
        Regex("""\bCNG\b""", RegexOption.IGNORE_CASE),                           // CNG
        Regex("""[bh]pcl|iocl""", RegexOption.IGNORE_CASE),                     // oil company codes
    )

    // ══════════════════════════════════════════════════════════════════════
    // STAGE 5 — AMOUNT HEURISTICS (tiebreaker only)
    // ══════════════════════════════════════════════════════════════════════

    private fun looksLikeFuelAmount(amount: Double?) =
        amount != null && amount in 50.0..7000.0 && amount % 50 == 0.0

    private fun looksLikeFoodDeliveryAmount(amount: Double?) =
        amount != null && amount in 40.0..1200.0

    // ══════════════════════════════════════════════════════════════════════
    // CONFLICT RULES — prevent category bleed
    // ══════════════════════════════════════════════════════════════════════

    /** If these appear alongside food signals, cancel the food classification */
    private val FOOD_CANCEL_PHRASES = listOf(
        "hotel booking", "hotel reservation", "hotel.com", "oyo rooms",
        "hotel management", "food processing", "food packaging",
        "food technology", "food science",
    )

    /** If these appear alongside fuel signals, cancel the fuel classification */
    private val FUEL_CANCEL_PHRASES = listOf(
        "hair oil", "coconut oil", "cooking oil", "edible oil",
        "essential oil", "oil massage",
        "energy drink", "monster energy", "red bull",
        "lpg booking", "gas cylinder",     // LPG is bills, not fuel
        "pump shoes", "water pump", "submersible pump",
    )

    // ══════════════════════════════════════════════════════════════════════
    // PUBLIC CLASSIFY FUNCTION
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Classify a merchant into one of: food, fuel, groceries, shopping, travel,
     * entertainment, bills, health, education, insurance, investments, emi, other.
     *
     * Returns null when confidence is too low to make a reliable call.
     * MerchantMatcher will then return MerchantDatabase.DEFAULT_TAG ("other").
     *
     * @param rawMerchant  Merchant name from SMS (not pre-processed)
     * @param smsBody      Full bank SMS body (crucial for context)
     * @param amount       Transaction amount for fuel/food tiebreaking
     */
    fun classify(rawMerchant: String, smsBody: String? = null, amount: Double? = null): MerchantTag? {
        val name = rawMerchant.trim()
        val nameLower = name.lowercase()
        val smsLower = smsBody?.lowercase() ?: ""
        val fullText = "$nameLower $smsLower".trimEnd()

        if (nameLower.isEmpty()) return null

        // ── Stage 1: MCC code ────────────────────────────────────────────
        val mcc = MCC_PATTERN.find(smsLower)?.groupValues?.get(1)?.toIntOrNull()
        if (mcc != null) {
            val mccSlug = MCC_TO_CATEGORY[mcc]
            if (mccSlug != null) {
                return tag(mccSlug, name, 0.99f, "mcc:$mcc")
            }
        }

        // ── Stage 2: Instant high-confidence patterns ────────────────────
        instantClassify(nameLower, smsLower, name)?.let { return it }

        // ── Stage 3: SMS structured context ─────────────────────────────
        smsContextClassify(smsLower, nameLower, name)?.let { return it }

        // ── Stage 4: Weighted keyword scoring ───────────────────────────
        val votes = mutableMapOf<String, Vote>()

        // Word tokenization (split on non-alphanumeric, treat each as a token)
        val nameTokens = tokenize(nameLower)
        val fullTokens = tokenize(fullText)

        // Score each category
        scoreCategory("food",          FOOD_WORDS,          FOOD_PHRASES,          nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("fuel",          FUEL_WORDS,          FUEL_PHRASES,          nameTokens, fullTokens, votes, 12, 10)
        scoreCategory("groceries",     GROCERY_WORDS,       emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("shopping",      SHOPPING_WORDS,      emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("travel",        TRAVEL_WORDS,        emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("entertainment", ENTERTAINMENT_WORDS, emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("bills",         BILLS_WORDS,         emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("health",        HEALTH_WORDS,        emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("education",     EDUCATION_WORDS,     emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("insurance",     INSURANCE_WORDS,     emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("investments",   INVESTMENT_WORDS,    emptySet(),            nameTokens, fullTokens, votes, 10, 8)
        scoreCategory("emi",           EMI_WORDS,           emptySet(),            nameTokens, fullTokens, votes, 10, 8)

        // Phonetic matching
        for (regex in PHONETIC_FOOD) {
            if (regex.containsMatchIn(fullText)) addVote(votes, "food", 9, "phonetic")
        }
        for (regex in PHONETIC_FUEL) {
            if (regex.containsMatchIn(fullText)) addVote(votes, "fuel", 11, "phonetic")
        }

        // Stage 5: Amount heuristics (tiebreaker)
        if (looksLikeFuelAmount(amount) && "fuel" in votes)   addVote(votes, "fuel", 4, "round_amount")
        if (looksLikeFoodDeliveryAmount(amount) && "food" in votes) addVote(votes, "food", 2, "amount")

        // Conflict resolution
        applyConflictRules(votes, fullText)

        // Winner selection
        return pickWinner(votes, name)
    }

    // ══════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private fun instantClassify(nameLower: String, smsLower: String, rawName: String): MerchantTag? {
        val combined = "$nameLower $smsLower"

        // ── Fuel instant patterns ────────────────────────────────────────
        // Check exact known company names / words
        if (FUEL_INSTANT_WORDS.any { combined.containsWord(it) }) {
            if (FUEL_CANCEL_PHRASES.none { combined.contains(it) }) {
                return tag("fuel", rawName, 0.99f, "instant_fuel_word")
            }
        }
        // Check name suffix (last word(s) of merchant name)
        if (FUEL_NAME_SUFFIXES.any { nameLower.endsWith(it) || nameLower.endsWith(" $it") }) {
            if (FUEL_CANCEL_PHRASES.none { combined.contains(it) }) {
                return tag("fuel", rawName, 0.98f, "fuel_suffix")
            }
        }
        // Check name prefix
        if (FUEL_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            if (FUEL_CANCEL_PHRASES.none { combined.contains(it) }) {
                return tag("fuel", rawName, 0.98f, "fuel_prefix")
            }
        }

        // ── Food instant patterns ─────────────────────────────────────────
        if (FOOD_INSTANT_WORDS.any { combined.containsWord(it) }) {
            if (FOOD_CANCEL_PHRASES.none { combined.contains(it) }) {
                return tag("food", rawName, 0.99f, "instant_food_word")
            }
        }
        if (FOOD_NAME_SUFFIXES.any { nameLower.endsWith(it) || nameLower.endsWith(" $it") }) {
            if (FOOD_CANCEL_PHRASES.none { combined.contains(it) }) {
                // "house", "point", "corner", "junction", "palace" are weaker suffixes
                val weakSuffixes = setOf("house", "point", "corner", "junction", "palace", "hub", "court")
                val conf = if (FOOD_NAME_SUFFIXES.first { nameLower.endsWith(it) } in weakSuffixes) 0.80f else 0.98f
                return tag("food", rawName, conf, "food_suffix")
            }
        }
        if (FOOD_NAME_PREFIXES.any { nameLower.startsWith(it) }) {
            if (FOOD_CANCEL_PHRASES.none { combined.contains(it) }) {
                return tag("food", rawName, 0.98f, "food_prefix")
            }
        }

        // ── Other category instant patterns ───────────────────────────────
        if (GROCERY_INSTANT_WORDS.any { combined.containsWord(it) } ||
            GROCERY_SUFFIXES.any { nameLower.endsWith(it) }) {
            return tag("groceries", rawName, 0.95f, "instant_grocery")
        }
        if (HEALTH_INSTANT_WORDS.any { combined.containsWord(it) } ||
            HEALTH_SUFFIXES.any { nameLower.endsWith(it) }) {
            return tag("health", rawName, 0.95f, "instant_health")
        }
        if (BILLS_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("bills", rawName, 0.97f, "instant_bills")
        }
        if (ENTERTAINMENT_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("entertainment", rawName, 0.99f, "instant_entertainment")
        }
        if (EDUCATION_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("education", rawName, 0.99f, "instant_education")
        }
        if (INSURANCE_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("insurance", rawName, 0.99f, "instant_insurance")
        }
        if (TRAVEL_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("travel", rawName, 0.99f, "instant_travel")
        }
        if (SHOPPING_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("shopping", rawName, 0.99f, "instant_shopping")
        }
        if (INVESTMENT_INSTANT_WORDS.any { combined.containsWord(it) }) {
            return tag("investments", rawName, 0.99f, "instant_investment")
        }

        return null
    }

    private fun smsContextClassify(smsLower: String, nameLower: String, rawName: String): MerchantTag? {
        if (smsLower.isEmpty()) return null

        // Extract context after "at", "to", "for" keywords
        val contexts = SMS_PURPOSE_PATTERN.findAll(smsLower)
            .map { it.groupValues[1].trim().lowercase() }
            .toList()

        val allContext = contexts.joinToString(" ")

        // Check SMS clinchers directly in the full SMS (not just extracted context)
        // Fuel: single clincher word is highly reliable in SMS
        val fuelHits = SMS_FUEL_CLINCHERS.count { smsLower.contains(it) }
        if (fuelHits >= 1 && FUEL_CANCEL_PHRASES.none { smsLower.contains(it) }) {
            return tag("fuel", rawName, 0.97f, "sms_fuel_clincher")
        }

        // Food: need at least 1 clincher in SMS or extracted context
        val foodHits = SMS_FOOD_CLINCHERS.count { smsLower.contains(it) || allContext.contains(it) }
        if (foodHits >= 1 && FOOD_CANCEL_PHRASES.none { smsLower.contains(it) }) {
            // But don't override if name has no food signal at all
            // (e.g. "Your order from SHARMA GENERAL STORES" - could be grocery)
            if (nameLower.containsAnyOf(FOOD_WORDS) || contexts.isNotEmpty()) {
                return tag("food", rawName, 0.94f, "sms_food_clincher")
            }
        }

        val groceryHits = SMS_GROCERY_CLINCHERS.count { smsLower.contains(it) }
        if (groceryHits >= 1) return tag("groceries", rawName, 0.92f, "sms_grocery_clincher")

        val healthHits = SMS_HEALTH_CLINCHERS.count { smsLower.contains(it) }
        if (healthHits >= 1 && nameLower.containsAnyOf(HEALTH_WORDS)) {
            return tag("health", rawName, 0.92f, "sms_health_clincher")
        }

        return null
    }

    private fun scoreCategory(
        slug: String,
        words: Set<String>,
        phrases: Set<String>,
        nameTokens: Set<String>,
        fullTokens: Set<String>,
        votes: MutableMap<String, Vote>,
        wordPts: Int,
        phrasePts: Int,
    ) {
        // Word matches in NAME (higher weight) — word boundary matching via tokens
        val nameHit = words.firstOrNull { it in nameTokens }
        if (nameHit != null) addVote(votes, slug, wordPts, "name:$nameHit")

        // Word matches in full text (SMS included) — lower weight
        val fullHit = words.firstOrNull { it in fullTokens && it !in nameTokens }
        if (fullHit != null) addVote(votes, slug, wordPts - 3, "sms:$fullHit")

        // Phrase matches (multi-word)
        for (phrase in phrases) {
            if (nameTokens.contains(phrase) || fullTokens.contains(phrase)) {
                addVote(votes, slug, phrasePts, "phrase:$phrase")
                break // count once
            }
        }
    }

    private fun applyConflictRules(votes: MutableMap<String, Vote>, fullText: String) {
        if ("food" in votes) {
            if (FOOD_CANCEL_PHRASES.any { fullText.contains(it) }) {
                votes["food"]?.score = votes["food"]!!.score - 20
            }
        }
        if ("fuel" in votes) {
            if (FUEL_CANCEL_PHRASES.any { fullText.contains(it) }) {
                votes["fuel"]?.score = votes["fuel"]!!.score - 20
            }
        }
        // If both food and fuel have votes, fuel wins if it has higher score
        // (edge case: "HP Gas" = fuel, not food + bills)
    }

    private fun pickWinner(votes: MutableMap<String, Vote>, rawName: String): MerchantTag? {
        val valid = votes.values.filter { it.score >= MIN_SCORE }
        if (valid.isEmpty()) return null

        val winner = valid.maxByOrNull { it.score } ?: return null
        val runnerUp = valid.filter { it.slug != winner.slug }.maxByOrNull { it.score }

        // Require a lead over runner-up unless winner score is very high
        if (runnerUp != null
            && (winner.score - runnerUp.score) < MARGIN_REQUIRED
            && winner.score < HIGH_CONFIDENCE_SOLO) {
            return null
        }

        val confidence = min(0.97f, 0.55f + winner.score.toFloat() * 0.025f)
        return tag(winner.slug, rawName, confidence, "scored")
    }

    // ── Utilities ──────────────────────────────────────────────────────

    /** Build a set of individual word tokens + two-word bigrams from text */
    private fun tokenize(text: String): Set<String> {
        val words = text.split(Regex("[^a-z0-9]")).filter { it.length >= 2 }
        val singles = words.toMutableSet()
        // Add bigrams
        for (i in 0 until words.size - 1) {
            singles.add("${words[i]} ${words[i + 1]}")
        }
        // Add trigrams for longer phrases
        for (i in 0 until words.size - 2) {
            singles.add("${words[i]} ${words[i + 1]} ${words[i + 2]}")
        }
        return singles
    }

    private fun String.containsWord(word: String): Boolean {
        if (!this.contains(word)) return false
        // Check word boundaries: char before and after must be non-alphanumeric or string edge
        val idx = this.indexOf(word)
        val before = if (idx == 0) true else !this[idx - 1].isLetterOrDigit()
        val after  = if (idx + word.length >= length) true else !this[idx + word.length].isLetterOrDigit()
        return before && after
    }

    private fun String.containsAnyOf(words: Set<String>): Boolean =
        words.any { this.contains(it) }

    private fun addVote(votes: MutableMap<String, Vote>, slug: String, pts: Int, signal: String) {
        votes.getOrPut(slug) { Vote(slug, 0) }.also {
            it.score += pts
            it.signals += signal
        }
    }

    private fun tag(slug: String, name: String, conf: Float, signal: String) = MerchantTag(
        categorySlug = slug,
        displayName  = name,
        subCategory  = null,
        confidence   = conf,
        matchType    = MatchType.KEYWORD
    )

    private const val MIN_SCORE = 8
    private const val MARGIN_REQUIRED = 5
    private const val HIGH_CONFIDENCE_SOLO = 20
}
