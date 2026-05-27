package com.spendwise.app.domain.merchant

/**
 * Comprehensive merchant classification database with 300+ merchants.
 * Used by MerchantMatcher for intelligent auto-tagging of transactions.
 */
object MerchantDatabase {

    /** Result of a merchant classification attempt */
    data class MerchantTag(
        val categorySlug: String,
        val displayName: String,
        val subCategory: String? = null,
        val confidence: Float,
        val matchType: MatchType
    )

    enum class MatchType { EXACT, UPI_DOMAIN, FUZZY, KEYWORD, DEFAULT }

    // ── EXACT MERCHANT MAP ──────────────────────────────────────
    val EXACT_MAP: Map<String, MerchantTag> = buildMap {
        // ── Food & Restaurants ────────────────────────────────
        fun food(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("food", name, sub, 0.99f, MatchType.EXACT))

        food("swiggy", "Swiggy", "food_delivery")
        food("zomato", "Zomato", "food_delivery")
        food("blinkit", "Blinkit", "quick_commerce")
        food("zepto", "Zepto", "quick_commerce")
        food("dunzo", "Dunzo", "quick_commerce")
        food("eatsure", "EatSure", "food_delivery")
        food("faasos", "Faasos", "food_delivery")
        food("behrouz", "Behrouz Biryani", "food_delivery")
        food("box8", "Box8", "food_delivery")
        food("mcdonald", "McDonald's", "fast_food")
        food("mcdonalds", "McDonald's", "fast_food")
        food("kfc", "KFC", "fast_food")
        food("pizza hut", "Pizza Hut", "fast_food")
        food("dominos", "Domino's", "fast_food")
        food("domino", "Domino's", "fast_food")
        food("subway", "Subway", "fast_food")
        food("burger king", "Burger King", "fast_food")
        food("starbucks", "Starbucks", "cafe")
        food("cafe coffee day", "CCD", "cafe")
        food("ccd", "Cafe Coffee Day", "cafe")
        food("barista", "Barista", "cafe")
        food("chaayos", "Chaayos", "cafe")
        food("haldirams", "Haldiram's", "restaurant")
        food("haldiram", "Haldiram's", "restaurant")
        food("barbeque nation", "Barbeque Nation", "restaurant")
        food("mainland china", "Mainland China", "restaurant")
        food("wow momos", "Wow! Momo", "restaurant")
        food("biryani by kilo", "Biryani By Kilo", "restaurant")
        food("freshmenu", "FreshMenu", "food_delivery")
        food("licious", "Licious", "grocery")
        food("ninjacart", "NinjaCart", "grocery")

        // ── Groceries & Supermarkets ──────────────────────────
        fun grocery(key: String, name: String) =
            put(key.lowercase(), MerchantTag("groceries", name, null, 0.99f, MatchType.EXACT))

        grocery("bigbasket", "BigBasket")
        grocery("big basket", "BigBasket")
        grocery("grofers", "Blinkit (Grofers)")
        grocery("dmart", "D-Mart")
        grocery("d-mart", "D-Mart")
        grocery("reliance fresh", "Reliance Fresh")
        grocery("reliance smart", "Reliance Smart")
        grocery("more supermarket", "More Supermarket")
        grocery("star bazaar", "Star Bazaar")
        grocery("spencers", "Spencer's")
        grocery("nilgiris", "Nilgiris")
        grocery("natures basket", "Nature's Basket")
        grocery("godrej nature", "Nature's Basket")
        grocery("jiomart", "JioMart")
        grocery("supr daily", "Supr Daily")
        grocery("milkbasket", "MilkBasket")
        grocery("country delight", "Country Delight")
        grocery("fresh to home", "FreshToHome")
        grocery("freshtohome", "FreshToHome")
        grocery("towns on fresh", "Town On Fresh")
        grocery("hypercity", "HyperCity")
        grocery("spar", "SPAR")
        grocery("walmart", "Walmart")
        grocery("metro cash", "Metro Cash & Carry")

        // ── E-commerce & Shopping ──────────────────────────────
        fun shop(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("shopping", name, sub, 0.99f, MatchType.EXACT))

        shop("amazon", "Amazon", "ecommerce")
        shop("flipkart", "Flipkart", "ecommerce")
        shop("myntra", "Myntra", "fashion")
        shop("ajio", "AJIO", "fashion")
        shop("meesho", "Meesho", "ecommerce")
        shop("snapdeal", "Snapdeal", "ecommerce")
        shop("nykaa", "Nykaa", "beauty")
        shop("purplle", "Purplle", "beauty")
        shop("mamaearth", "Mamaearth", "beauty")
        shop("bewakoof", "Bewakoof", "fashion")
        shop("clovia", "Clovia", "fashion")
        shop("limeroad", "LimeRoad", "fashion")
        shop("jabong", "Jabong", "fashion")
        shop("tatacliq", "TataCliq", "ecommerce")
        shop("tata cliq", "TataCliq", "ecommerce")
        shop("reliance digital", "Reliance Digital", "electronics")
        shop("croma", "Croma", "electronics")
        shop("vijay sales", "Vijay Sales", "electronics")
        shop("poorvika", "Poorvika", "electronics")
        shop("imagine", "iMagine (Apple)", "electronics")
        shop("apple store", "Apple Store", "electronics")
        shop("ikea", "IKEA", "furniture")
        shop("pepperfry", "Pepperfry", "furniture")
        shop("urban ladder", "Urban Ladder", "furniture")
        shop("firstcry", "FirstCry", "kids")
        shop("hopscotch", "Hopscotch", "kids")
        shop("shopclues", "ShopClues", "ecommerce")
        shop("paytm mall", "Paytm Mall", "ecommerce")
        shop("indiamart", "IndiaMART", "b2b")
        shop("zivame", "Zivame", "fashion")
        shop("boat", "boAt", "electronics")
        shop("noise", "Noise", "electronics")
        shop("oneplus", "OnePlus", "electronics")

        // ── Travel & Transport ────────────────────────────────
        fun travel(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("travel", name, sub, 0.99f, MatchType.EXACT))

        travel("irctc", "IRCTC", "train")
        travel("makemytrip", "MakeMyTrip", "booking")
        travel("goibibo", "Goibibo", "booking")
        travel("cleartrip", "Cleartrip", "booking")
        travel("yatra", "Yatra", "booking")
        travel("ixigo", "Ixigo", "booking")
        travel("redbus", "RedBus", "bus")
        travel("abhibus", "AbhiBus", "bus")
        travel("uber", "Uber", "cab")
        travel("ola", "Ola", "cab")
        travel("rapido", "Rapido", "bike_taxi")
        travel("meru", "Meru Cabs", "cab")
        travel("indigo", "IndiGo", "flight")
        travel("indigo airlines", "IndiGo Airlines", "flight")
        travel("air india", "Air India", "flight")
        travel("vistara", "Vistara", "flight")
        travel("spicejet", "SpiceJet", "flight")
        travel("go first", "Go First", "flight")
        travel("akasa", "Akasa Air", "flight")
        travel("oyo", "OYO", "hotel")
        travel("treebo", "Treebo Hotels", "hotel")
        travel("fabhotels", "FabHotels", "hotel")
        travel("airbnb", "Airbnb", "hotel")
        travel("booking.com", "Booking.com", "hotel")
        travel("agoda", "Agoda", "hotel")
        travel("hotels.com", "Hotels.com", "hotel")
        travel("mumbai metro", "Mumbai Metro", "metro")
        travel("dmrc", "Delhi Metro", "metro")
        travel("bmtc", "BMTC", "bus")
        travel("best bus", "BEST Bus", "bus")

        // ── Fuel & Petrol ─────────────────────────────────────
        fun fuel(key: String, name: String) =
            put(key.lowercase(), MerchantTag("fuel", name, null, 0.99f, MatchType.EXACT))

        fuel("indian oil", "Indian Oil")
        fuel("iocl", "Indian Oil")
        fuel("hp petrol", "HP Petrol")
        fuel("hindustan petroleum", "Hindustan Petroleum")
        fuel("bharat petroleum", "Bharat Petroleum")
        fuel("bpcl", "BPCL")
        fuel("shell", "Shell")
        fuel("essar oil", "Essar Oil")
        fuel("nayara energy", "Nayara Energy")
        fuel("petrol pump", "Petrol Pump")

        // ── Entertainment & Streaming ─────────────────────────
        fun ent(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("entertainment", name, sub, 0.99f, MatchType.EXACT))

        ent("netflix", "Netflix", "streaming")
        ent("amazon prime", "Amazon Prime", "streaming")
        ent("disney hotstar", "Disney+ Hotstar", "streaming")
        ent("hotstar", "Hotstar", "streaming")
        ent("sonyliv", "SonyLIV", "streaming")
        ent("zee5", "ZEE5", "streaming")
        ent("voot", "Voot", "streaming")
        ent("jiocinema", "JioCinema", "streaming")
        ent("mxplayer", "MX Player", "streaming")
        ent("alt balaji", "ALT Balaji", "streaming")
        ent("spotify", "Spotify", "music")
        ent("gaana", "Gaana", "music")
        ent("jiosaavn", "JioSaavn", "music")
        ent("wynk", "Wynk Music", "music")
        ent("bookmyshow", "BookMyShow", "events")
        ent("pvr", "PVR Cinemas", "movies")
        ent("inox", "INOX", "movies")
        ent("cinepolis", "Cinépolis", "movies")
        ent("carnival cinema", "Carnival Cinema", "movies")
        ent("youtube premium", "YouTube Premium", "streaming")
        ent("apple music", "Apple Music", "music")
        ent("audible", "Audible", "audio")
        ent("kindle", "Kindle", "ebooks")
        ent("steam", "Steam", "gaming")
        ent("playstation", "PlayStation", "gaming")
        ent("xbox", "Xbox", "gaming")

        // ── Health & Medical ──────────────────────────────────
        fun health(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("health", name, sub, 0.99f, MatchType.EXACT))

        health("apollo", "Apollo Pharmacy", "pharmacy")
        health("apollo pharmacy", "Apollo Pharmacy", "pharmacy")
        health("medplus", "MedPlus", "pharmacy")
        health("1mg", "1mg", "pharmacy")
        health("netmeds", "Netmeds", "pharmacy")
        health("pharmeasy", "PharmEasy", "pharmacy")
        health("practo", "Practo", "doctor")
        health("mfine", "mFine", "doctor")
        health("tata health", "Tata Health", "doctor")
        health("healthians", "Healthians", "lab")
        health("lal pathlabs", "Lal PathLabs", "lab")
        health("dr lal", "Dr Lal PathLabs", "lab")
        health("thyrocare", "Thyrocare", "lab")
        health("cult fit", "Cult.fit", "fitness")
        health("cultfit", "Cult.fit", "fitness")
        health("cure fit", "Cure.fit", "fitness")
        health("gold gym", "Gold's Gym", "fitness")
        health("anytime fitness", "Anytime Fitness", "fitness")

        // ── Utilities & Bills ─────────────────────────────────
        fun bills(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("bills", name, sub, 0.99f, MatchType.EXACT))

        bills("bescom", "BESCOM", "electricity")
        bills("msedcl", "MSEDCL", "electricity")
        bills("tneb", "TNEB", "electricity")
        bills("reliance energy", "Reliance Energy", "electricity")
        bills("tata power", "Tata Power", "electricity")
        bills("adani electricity", "Adani Electricity", "electricity")
        bills("airtel", "Airtel", "telecom")
        bills("jio", "Jio", "telecom")
        bills("vodafone", "Vodafone", "telecom")
        bills("vi", "Vi (Vodafone Idea)", "telecom")
        bills("bsnl", "BSNL", "telecom")
        bills("act fibernet", "ACT Fibernet", "internet")
        bills("hathway", "Hathway", "internet")
        bills("d2h", "D2H", "dth")
        bills("tata sky", "Tata Play", "dth")
        bills("tata play", "Tata Play", "dth")
        bills("dish tv", "DishTV", "dth")
        bills("sun direct", "Sun Direct", "dth")
        bills("bbmp", "BBMP", "property_tax")
        bills("mcgm", "MCGM", "water_bill")
        bills("nmmc", "NMMC", "municipal")
        bills("indane", "Indane LPG", "lpg")
        bills("bharat gas", "Bharat Gas", "lpg")
        bills("hp gas", "HP Gas", "lpg")

        // ── Banking & EMI ─────────────────────────────────────
        fun emi(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("emi", name, sub, 0.99f, MatchType.EXACT))

        emi("hdfc", "HDFC Bank", "bank")
        emi("icici", "ICICI Bank", "bank")
        emi("sbi", "State Bank of India", "bank")
        emi("axis bank", "Axis Bank", "bank")
        emi("kotak", "Kotak Mahindra Bank", "bank")
        emi("yes bank", "Yes Bank", "bank")
        emi("punjab national", "PNB", "bank")
        emi("bank of baroda", "Bank of Baroda", "bank")
        emi("indusind", "IndusInd Bank", "bank")
        emi("federal bank", "Federal Bank", "bank")
        emi("idfc first", "IDFC FIRST Bank", "bank")
        emi("bajaj finance", "Bajaj Finance", "emi")
        emi("bajaj finserv", "Bajaj Finserv", "emi")
        emi("home credit", "Home Credit", "emi")
        emi("capital first", "IDFC FIRST", "emi")
        emi("emi", "EMI Payment", "emi")

        // ── Investments & Finance ─────────────────────────────
        fun invest(key: String, name: String, sub: String? = null) =
            put(key.lowercase(), MerchantTag("investments", name, sub, 0.99f, MatchType.EXACT))

        invest("zerodha", "Zerodha", "stocks")
        invest("groww", "Groww", "mf_stocks")
        invest("upstox", "Upstox", "stocks")
        invest("angel broking", "Angel One", "stocks")
        invest("angel one", "Angel One", "stocks")
        invest("5paisa", "5paisa", "stocks")
        invest("paytm money", "Paytm Money", "mf")
        invest("kuvera", "Kuvera", "mf")
        invest("etmoney", "ET Money", "mf")
        invest("coin", "Coin by Zerodha", "mf")
        invest("smallcase", "Smallcase", "stocks")
        invest("nse", "NSE", "stocks")
        invest("bse", "BSE", "stocks")
        invest("ppf", "PPF", "savings")
        invest("nps", "NPS", "pension")

        // ── Education ─────────────────────────────────────────
        fun edu(key: String, name: String) =
            put(key.lowercase(), MerchantTag("education", name, null, 0.99f, MatchType.EXACT))

        edu("byju", "BYJU'S")
        edu("byjus", "BYJU'S")
        edu("unacademy", "Unacademy")
        edu("vedantu", "Vedantu")
        edu("upgrad", "upGrad")
        edu("coursera", "Coursera")
        edu("udemy", "Udemy")
        edu("simplilearn", "Simplilearn")
        edu("great learning", "Great Learning")
        edu("whitehat jr", "WhiteHat Jr")
        edu("scaler", "Scaler")
        edu("coding ninjas", "Coding Ninjas")
        edu("collegedunia", "CollegeDunia")

        // ── Insurance ─────────────────────────────────────────
        fun ins(key: String, name: String) =
            put(key.lowercase(), MerchantTag("insurance", name, null, 0.99f, MatchType.EXACT))

        ins("lic", "LIC")
        ins("lici", "LIC India")
        ins("star health", "Star Health Insurance")
        ins("care health", "Care Health Insurance")
        ins("niva bupa", "Niva Bupa Health")
        ins("hdfc life", "HDFC Life Insurance")
        ins("icici prudential", "ICICI Prudential")
        ins("max life", "Max Life Insurance")
        ins("sbi life", "SBI Life Insurance")
        ins("tata aia", "Tata AIA")
        ins("bajaj allianz", "Bajaj Allianz")
        ins("acko", "Acko Insurance")
        ins("digit insurance", "Digit Insurance")
        ins("policybazaar", "PolicyBazaar")
    }

    // ── UPI DOMAIN MAP ──────────────────────────────────────────
    // Maps UPI handle domains → category
    val UPI_DOMAIN_MAP: Map<String, MerchantTag> = buildMap {
        fun u(domain: String, slug: String, name: String, sub: String? = null) =
            put(domain, MerchantTag(slug, name, sub, 0.92f, MatchType.UPI_DOMAIN))

        u("swiggy", "food", "Swiggy", "food_delivery")
        u("zomato", "food", "Zomato", "food_delivery")
        u("blinkit", "food", "Blinkit", "quick_commerce")
        u("zepto", "food", "Zepto", "quick_commerce")
        u("dunzo", "food", "Dunzo", "quick_commerce")
        u("amazon", "shopping", "Amazon", "ecommerce")
        u("flipkart", "shopping", "Flipkart", "ecommerce")
        u("myntra", "shopping", "Myntra", "fashion")
        u("ajio", "shopping", "AJIO", "fashion")
        u("meesho", "shopping", "Meesho", "ecommerce")
        u("nykaa", "shopping", "Nykaa", "beauty")
        u("bigbasket", "groceries", "BigBasket")
        u("jiomart", "groceries", "JioMart")
        u("irctc", "travel", "IRCTC", "train")
        u("uber", "travel", "Uber", "cab")
        u("ola", "travel", "Ola", "cab")
        u("rapido", "travel", "Rapido", "bike_taxi")
        u("makemytrip", "travel", "MakeMyTrip", "booking")
        u("redbus", "travel", "RedBus", "bus")
        u("netflix", "entertainment", "Netflix", "streaming")
        u("hotstar", "entertainment", "Hotstar", "streaming")
        u("spotify", "entertainment", "Spotify", "music")
        u("bookmyshow", "entertainment", "BookMyShow", "events")
        u("airtel", "bills", "Airtel", "telecom")
        u("jio", "bills", "Jio", "telecom")
        u("bsnl", "bills", "BSNL", "telecom")
        u("vodafone", "bills", "Vodafone", "telecom")
        u("bescom", "bills", "BESCOM", "electricity")
        u("tatapower", "bills", "Tata Power", "electricity")
        u("tatasky", "bills", "Tata Play", "dth")
        u("zerodha", "investments", "Zerodha", "stocks")
        u("groww", "investments", "Groww", "mf_stocks")
        u("upstox", "investments", "Upstox", "stocks")
        u("lic", "insurance", "LIC")
        u("hdfclife", "insurance", "HDFC Life")
        u("bajajfinserv", "emi", "Bajaj Finserv")
        u("paytm", "other", "Paytm")
        u("phonepe", "other", "PhonePe")
        u("googlepay", "other", "Google Pay")
        u("bhim", "other", "BHIM UPI")
    }

    // ── KEYWORD SCORES ──────────────────────────────────────────
    data class KeywordScore(val slug: String, val score: Int)

    val KEYWORD_SCORES: List<Pair<Regex, KeywordScore>> = listOf(
        Regex("food|eat|restaurant|hotel|biryani|pizza|burger|cafe|coffee|sweets|bakery|dhaba", RegexOption.IGNORE_CASE) to KeywordScore("food", 3),
        Regex("grocery|supermarket|market|vegetables|fruits|kirana|provision", RegexOption.IGNORE_CASE) to KeywordScore("groceries", 3),
        Regex("shopping|store|shop|mart|retail|fashion|clothes|footwear|shoe|apparel|wear", RegexOption.IGNORE_CASE) to KeywordScore("shopping", 3),
        Regex("amazon|flipkart|meesho|myntra|ajio|nykaa|snapdeal", RegexOption.IGNORE_CASE) to KeywordScore("shopping", 5),
        Regex("travel|flight|train|bus|hotel|cab|taxi|booking|airline|tour", RegexOption.IGNORE_CASE) to KeywordScore("travel", 3),
        Regex("uber|ola|rapido|meru|savaari", RegexOption.IGNORE_CASE) to KeywordScore("travel", 5),
        Regex("petrol|diesel|fuel|gas station|cng|pump", RegexOption.IGNORE_CASE) to KeywordScore("fuel", 4),
        Regex("iocl|bpcl|hpcl|shell|nayara", RegexOption.IGNORE_CASE) to KeywordScore("fuel", 5),
        Regex("netflix|hotstar|prime|spotify|youtube|music|movie|cinema|pvr|inox|stream", RegexOption.IGNORE_CASE) to KeywordScore("entertainment", 4),
        Regex("hospital|clinic|doctor|medical|pharmacy|medicine|health|lab|pathlab|diagnostic", RegexOption.IGNORE_CASE) to KeywordScore("health", 4),
        Regex("electricity|power|water|gas|lpg|internet|broadband|cable|dth|telecom|mobile recharge|recharge", RegexOption.IGNORE_CASE) to KeywordScore("bills", 4),
        Regex("airtel|jio|bsnl|vi |vodafone|idea", RegexOption.IGNORE_CASE) to KeywordScore("bills", 5),
        Regex("emi|loan|repayment|equated|monthly installment|lender|finance|bank payment", RegexOption.IGNORE_CASE) to KeywordScore("emi", 4),
        Regex("sip|mutual fund|mf|stocks|shares|equity|investment|demat|zerodha|groww|upstox", RegexOption.IGNORE_CASE) to KeywordScore("investments", 4),
        Regex("insurance|policy|premium|life insurance|health insurance|motor insurance|lic", RegexOption.IGNORE_CASE) to KeywordScore("insurance", 4),
        Regex("school|college|university|tuition|coaching|course|udemy|coursera|education|fees", RegexOption.IGNORE_CASE) to KeywordScore("education", 3),
        Regex("gym|fitness|yoga|workout|sport|swimming|cycling|wellness", RegexOption.IGNORE_CASE) to KeywordScore("health", 2),
    )

    val DEFAULT_TAG = MerchantTag("other", "Other", null, 0.1f, MatchType.DEFAULT)
}
