/**
 * SpendWise Intelligent Merchant Classification Engine
 * Multi-tier matching: Exact → UPI Domain → Fuzzy (Jaro-Winkler) → Keyword Scoring
 * 300+ merchants across 14 categories with confidence scoring
 */

export interface MerchantTag {
  categorySlug: string;
  displayName: string;
  subCategory?: string;
  confidence: number;
  matchType: 'exact' | 'upi_domain' | 'fuzzy' | 'keyword' | 'default';
}

// ── TIER 1: EXACT MERCHANT MAP ─────────────────────────────────────────────
// Key = normalized lowercase merchant name, Value = category + canonical name
const EXACT_MAP: Record<string, { cat: string; name: string; sub?: string }> = {
  // ── FOOD DELIVERY & RESTAURANTS ──
  'swiggy': { cat: 'food', name: 'Swiggy', sub: 'delivery' },
  'zomato': { cat: 'food', name: 'Zomato', sub: 'delivery' },
  'dunzo': { cat: 'food', name: 'Dunzo', sub: 'delivery' },
  'box8': { cat: 'food', name: 'Box8', sub: 'delivery' },
  'freshmenu': { cat: 'food', name: 'FreshMenu', sub: 'delivery' },
  'faasos': { cat: 'food', name: 'Faasos', sub: 'delivery' },
  'licious': { cat: 'food', name: 'Licious', sub: 'delivery' },
  'country delight': { cat: 'food', name: 'Country Delight', sub: 'dairy' },
  'milkbasket': { cat: 'food', name: 'Milkbasket', sub: 'dairy' },
  'dominos': { cat: 'food', name: "Domino's", sub: 'restaurant' },
  "domino's": { cat: 'food', name: "Domino's", sub: 'restaurant' },
  'pizza hut': { cat: 'food', name: 'Pizza Hut', sub: 'restaurant' },
  'kfc': { cat: 'food', name: 'KFC', sub: 'restaurant' },
  'mcdonalds': { cat: 'food', name: "McDonald's", sub: 'restaurant' },
  "mcdonald's": { cat: 'food', name: "McDonald's", sub: 'restaurant' },
  'burger king': { cat: 'food', name: 'Burger King', sub: 'restaurant' },
  'subway': { cat: 'food', name: 'Subway', sub: 'restaurant' },
  'starbucks': { cat: 'food', name: 'Starbucks', sub: 'cafe' },
  'cafe coffee day': { cat: 'food', name: 'Café Coffee Day', sub: 'cafe' },
  'ccd': { cat: 'food', name: 'CCD', sub: 'cafe' },
  'chaayos': { cat: 'food', name: 'Chaayos', sub: 'cafe' },
  'barista': { cat: 'food', name: 'Barista', sub: 'cafe' },
  'haldirams': { cat: 'food', name: 'Haldirams', sub: 'restaurant' },
  "haldiram's": { cat: 'food', name: 'Haldirams', sub: 'restaurant' },
  'mtr': { cat: 'food', name: 'MTR', sub: 'restaurant' },
  'saravana bhavan': { cat: 'food', name: 'Saravana Bhavan', sub: 'restaurant' },
  'behrouz biryani': { cat: 'food', name: 'Behrouz Biryani', sub: 'delivery' },
  'paradise biryani': { cat: 'food', name: 'Paradise Biryani', sub: 'restaurant' },
  'barbeque nation': { cat: 'food', name: 'Barbeque Nation', sub: 'restaurant' },
  'biryani by kilo': { cat: 'food', name: 'Biryani By Kilo', sub: 'delivery' },
  'rollacosta': { cat: 'food', name: 'Rollacosta', sub: 'restaurant' },

  // ── GROCERIES & QUICK COMMERCE ──
  'blinkit': { cat: 'groceries', name: 'Blinkit', sub: 'quick_commerce' },
  'zepto': { cat: 'groceries', name: 'Zepto', sub: 'quick_commerce' },
  'bigbasket': { cat: 'groceries', name: 'BigBasket', sub: 'online_grocery' },
  'bb daily': { cat: 'groceries', name: 'BB Daily', sub: 'subscription' },
  'nature basket': { cat: 'groceries', name: "Nature's Basket", sub: 'premium_grocery' },
  'spencers': { cat: 'groceries', name: "Spencer's", sub: 'supermarket' },
  "spencer's": { cat: 'groceries', name: "Spencer's", sub: 'supermarket' },
  'dmart': { cat: 'groceries', name: 'DMart', sub: 'supermarket' },
  'd-mart': { cat: 'groceries', name: 'DMart', sub: 'supermarket' },
  'big bazaar': { cat: 'groceries', name: 'Big Bazaar', sub: 'supermarket' },
  'reliance smart': { cat: 'groceries', name: 'Reliance Smart', sub: 'supermarket' },
  'jiomart': { cat: 'groceries', name: 'JioMart', sub: 'online_grocery' },
  'more supermarket': { cat: 'groceries', name: 'More Supermarket', sub: 'supermarket' },
  'star bazaar': { cat: 'groceries', name: 'Star Bazaar', sub: 'supermarket' },
  'swiggy instamart': { cat: 'groceries', name: 'Swiggy Instamart', sub: 'quick_commerce' },
  'instamart': { cat: 'groceries', name: 'Swiggy Instamart', sub: 'quick_commerce' },
  'grofers': { cat: 'groceries', name: 'Blinkit', sub: 'quick_commerce' },
  'milkman': { cat: 'groceries', name: 'Milkman', sub: 'dairy' },

  // ── ECOMMERCE & SHOPPING ──
  'amazon': { cat: 'shopping', name: 'Amazon', sub: 'ecommerce' },
  'flipkart': { cat: 'shopping', name: 'Flipkart', sub: 'ecommerce' },
  'myntra': { cat: 'shopping', name: 'Myntra', sub: 'fashion' },
  'ajio': { cat: 'shopping', name: 'Ajio', sub: 'fashion' },
  'nykaa': { cat: 'shopping', name: 'Nykaa', sub: 'beauty' },
  'nykaa fashion': { cat: 'shopping', name: 'Nykaa Fashion', sub: 'fashion' },
  'meesho': { cat: 'shopping', name: 'Meesho', sub: 'ecommerce' },
  'snapdeal': { cat: 'shopping', name: 'Snapdeal', sub: 'ecommerce' },
  'shopclues': { cat: 'shopping', name: 'ShopClues', sub: 'ecommerce' },
  'tata cliq': { cat: 'shopping', name: 'Tata CLiQ', sub: 'ecommerce' },
  'tatacliq': { cat: 'shopping', name: 'Tata CLiQ', sub: 'ecommerce' },
  'firstcry': { cat: 'shopping', name: 'FirstCry', sub: 'kids' },
  'limeroad': { cat: 'shopping', name: 'LimeRoad', sub: 'fashion' },
  'jabong': { cat: 'shopping', name: 'Jabong', sub: 'fashion' },
  'pepperfry': { cat: 'shopping', name: 'Pepperfry', sub: 'furniture' },
  'urban ladder': { cat: 'shopping', name: 'Urban Ladder', sub: 'furniture' },
  'ikea': { cat: 'shopping', name: 'IKEA', sub: 'furniture' },
  'croma': { cat: 'shopping', name: 'Croma', sub: 'electronics' },
  'vijay sales': { cat: 'shopping', name: 'Vijay Sales', sub: 'electronics' },
  'poorvika': { cat: 'shopping', name: 'Poorvika', sub: 'electronics' },
  'reliance digital': { cat: 'shopping', name: 'Reliance Digital', sub: 'electronics' },
  'lenskart': { cat: 'shopping', name: 'Lenskart', sub: 'eyewear' },
  'lifestyle': { cat: 'shopping', name: 'Lifestyle', sub: 'fashion' },
  'westside': { cat: 'shopping', name: 'Westside', sub: 'fashion' },
  'pantaloons': { cat: 'shopping', name: 'Pantaloons', sub: 'fashion' },
  'max fashion': { cat: 'shopping', name: 'Max Fashion', sub: 'fashion' },
  'zara': { cat: 'shopping', name: 'Zara', sub: 'fashion' },
  'h&m': { cat: 'shopping', name: 'H&M', sub: 'fashion' },
  'uniqlo': { cat: 'shopping', name: 'Uniqlo', sub: 'fashion' },
  'boat lifestyle': { cat: 'shopping', name: 'boAt Lifestyle', sub: 'electronics' },
  'mivi': { cat: 'shopping', name: 'Mivi', sub: 'electronics' },
  'purplle': { cat: 'shopping', name: 'Purplle', sub: 'beauty' },
  'mamaearth': { cat: 'shopping', name: 'Mamaearth', sub: 'beauty' },
  'the body shop': { cat: 'shopping', name: 'The Body Shop', sub: 'beauty' },
  'forest essentials': { cat: 'shopping', name: 'Forest Essentials', sub: 'beauty' },
  'sugar cosmetics': { cat: 'shopping', name: 'Sugar Cosmetics', sub: 'beauty' },

  // ── TRAVEL & TRANSPORT ──
  'irctc': { cat: 'travel', name: 'IRCTC', sub: 'rail' },
  'ola': { cat: 'travel', name: 'Ola', sub: 'cab' },
  'uber': { cat: 'travel', name: 'Uber', sub: 'cab' },
  'rapido': { cat: 'travel', name: 'Rapido', sub: 'bike_taxi' },
  'ola electric': { cat: 'travel', name: 'Ola Electric', sub: 'ev' },
  'bounce': { cat: 'travel', name: 'Bounce', sub: 'scooter' },
  'yulu': { cat: 'travel', name: 'Yulu', sub: 'bike_share' },
  'makemytrip': { cat: 'travel', name: 'MakeMyTrip', sub: 'booking' },
  'cleartrip': { cat: 'travel', name: 'Cleartrip', sub: 'booking' },
  'yatra': { cat: 'travel', name: 'Yatra', sub: 'booking' },
  'easemytrip': { cat: 'travel', name: 'EaseMyTrip', sub: 'booking' },
  'ixigo': { cat: 'travel', name: 'ixigo', sub: 'booking' },
  'redbus': { cat: 'travel', name: 'redBus', sub: 'bus' },
  'abhibus': { cat: 'travel', name: 'AbhiBus', sub: 'bus' },
  'indigo': { cat: 'travel', name: 'IndiGo', sub: 'flight' },
  'air india': { cat: 'travel', name: 'Air India', sub: 'flight' },
  'spicejet': { cat: 'travel', name: 'SpiceJet', sub: 'flight' },
  'vistara': { cat: 'travel', name: 'Vistara', sub: 'flight' },
  'akasa air': { cat: 'travel', name: 'Akasa Air', sub: 'flight' },
  'go first': { cat: 'travel', name: 'Go First', sub: 'flight' },
  'goibibo': { cat: 'travel', name: 'Goibibo', sub: 'booking' },
  'oyo': { cat: 'travel', name: 'OYO', sub: 'hotel' },
  'treebo': { cat: 'travel', name: 'Treebo', sub: 'hotel' },
  'fab hotels': { cat: 'travel', name: 'Fab Hotels', sub: 'hotel' },
  'mmt hotels': { cat: 'travel', name: 'MMT Hotels', sub: 'hotel' },

  // ── FUEL & VEHICLE ──
  'hp petrol': { cat: 'fuel', name: 'HP Petrol', sub: 'petrol' },
  'hindustan petroleum': { cat: 'fuel', name: 'HPCL', sub: 'petrol' },
  'hpcl': { cat: 'fuel', name: 'HPCL', sub: 'petrol' },
  'indian oil': { cat: 'fuel', name: 'IndianOil', sub: 'petrol' },
  'indianoil': { cat: 'fuel', name: 'IndianOil', sub: 'petrol' },
  'iocl': { cat: 'fuel', name: 'IOCL', sub: 'petrol' },
  'bharat petroleum': { cat: 'fuel', name: 'BPCL', sub: 'petrol' },
  'bpcl': { cat: 'fuel', name: 'BPCL', sub: 'petrol' },
  'shell': { cat: 'fuel', name: 'Shell', sub: 'petrol' },
  'essar petrol': { cat: 'fuel', name: 'Essar', sub: 'petrol' },
  'nayara': { cat: 'fuel', name: 'Nayara Energy', sub: 'petrol' },
  'cng': { cat: 'fuel', name: 'CNG', sub: 'gas' },
  'fastag': { cat: 'fuel', name: 'FASTag', sub: 'toll' },
  'netc fastag': { cat: 'fuel', name: 'NETC FASTag', sub: 'toll' },

  // ── ENTERTAINMENT & OTT ──
  'netflix': { cat: 'entertainment', name: 'Netflix', sub: 'ott' },
  'amazon prime': { cat: 'entertainment', name: 'Amazon Prime', sub: 'ott' },
  'hotstar': { cat: 'entertainment', name: 'Disney+ Hotstar', sub: 'ott' },
  'disney hotstar': { cat: 'entertainment', name: 'Disney+ Hotstar', sub: 'ott' },
  'sonyliv': { cat: 'entertainment', name: 'SonyLIV', sub: 'ott' },
  'zee5': { cat: 'entertainment', name: 'ZEE5', sub: 'ott' },
  'voot': { cat: 'entertainment', name: 'Voot', sub: 'ott' },
  'jiocinema': { cat: 'entertainment', name: 'JioCinema', sub: 'ott' },
  'mxplayer': { cat: 'entertainment', name: 'MX Player', sub: 'ott' },
  'aha': { cat: 'entertainment', name: 'aha', sub: 'ott' },
  'spotify': { cat: 'entertainment', name: 'Spotify', sub: 'music' },
  'gaana': { cat: 'entertainment', name: 'Gaana', sub: 'music' },
  'wynk music': { cat: 'entertainment', name: 'Wynk Music', sub: 'music' },
  'youtube premium': { cat: 'entertainment', name: 'YouTube Premium', sub: 'streaming' },
  'pvr': { cat: 'entertainment', name: 'PVR Cinemas', sub: 'cinema' },
  'inox': { cat: 'entertainment', name: 'INOX', sub: 'cinema' },
  'cinepolis': { cat: 'entertainment', name: 'Cinepolis', sub: 'cinema' },
  'bookmyshow': { cat: 'entertainment', name: 'BookMyShow', sub: 'ticketing' },
  'insider': { cat: 'entertainment', name: 'Insider', sub: 'ticketing' },

  // ── HEALTH & WELLNESS ──
  'apollo pharmacy': { cat: 'health', name: 'Apollo Pharmacy', sub: 'pharmacy' },
  'apollo247': { cat: 'health', name: 'Apollo 247', sub: 'telemedicine' },
  'medplus': { cat: 'health', name: 'MedPlus', sub: 'pharmacy' },
  'netmeds': { cat: 'health', name: 'Netmeds', sub: 'pharmacy' },
  '1mg': { cat: 'health', name: '1mg', sub: 'pharmacy' },
  'pharmeasy': { cat: 'health', name: 'PharmEasy', sub: 'pharmacy' },
  'practo': { cat: 'health', name: 'Practo', sub: 'telemedicine' },
  'healthkart': { cat: 'health', name: 'HealthKart', sub: 'supplements' },
  'cult fit': { cat: 'health', name: 'cult.fit', sub: 'fitness' },
  'cultfit': { cat: 'health', name: 'cult.fit', sub: 'fitness' },
  'healthifyme': { cat: 'health', name: 'HealthifyMe', sub: 'fitness' },
  'lal pathlabs': { cat: 'health', name: 'Lal PathLabs', sub: 'diagnostics' },
  'dr lal': { cat: 'health', name: 'Lal PathLabs', sub: 'diagnostics' },
  'metropolis': { cat: 'health', name: 'Metropolis', sub: 'diagnostics' },
  'thyrocare': { cat: 'health', name: 'Thyrocare', sub: 'diagnostics' },

  // ── BILLS & UTILITIES ──
  'airtel': { cat: 'bills', name: 'Airtel', sub: 'telecom' },
  'jio': { cat: 'bills', name: 'Jio', sub: 'telecom' },
  'reliance jio': { cat: 'bills', name: 'Jio', sub: 'telecom' },
  'bsnl': { cat: 'bills', name: 'BSNL', sub: 'telecom' },
  'vodafone': { cat: 'bills', name: 'Vodafone Idea', sub: 'telecom' },
  'vi ': { cat: 'bills', name: 'Vi', sub: 'telecom' },
  'vodafone idea': { cat: 'bills', name: 'Vodafone Idea', sub: 'telecom' },
  'act fibernet': { cat: 'bills', name: 'ACT Fibernet', sub: 'broadband' },
  'hathway': { cat: 'bills', name: 'Hathway', sub: 'broadband' },
  'spectranet': { cat: 'bills', name: 'Spectranet', sub: 'broadband' },
  'tata play': { cat: 'bills', name: 'Tata Play', sub: 'dth' },
  'dish tv': { cat: 'bills', name: 'Dish TV', sub: 'dth' },
  'sun direct': { cat: 'bills', name: 'Sun Direct', sub: 'dth' },
  'd2h': { cat: 'bills', name: 'd2h', sub: 'dth' },
  'videocon d2h': { cat: 'bills', name: 'd2h', sub: 'dth' },
  'bescom': { cat: 'bills', name: 'BESCOM', sub: 'electricity' },
  'tneb': { cat: 'bills', name: 'TNEB', sub: 'electricity' },
  'msedcl': { cat: 'bills', name: 'MSEDCL', sub: 'electricity' },
  'bses': { cat: 'bills', name: 'BSES', sub: 'electricity' },
  'cesc': { cat: 'bills', name: 'CESC', sub: 'electricity' },
  'adani electricity': { cat: 'bills', name: 'Adani Electricity', sub: 'electricity' },
  'tata power': { cat: 'bills', name: 'Tata Power', sub: 'electricity' },
  'mahadiscom': { cat: 'bills', name: 'Mahadiscom', sub: 'electricity' },
  'wesco': { cat: 'bills', name: 'WESCO', sub: 'electricity' },
  'nbpdcl': { cat: 'bills', name: 'NBPDCL', sub: 'electricity' },
  'pgvcl': { cat: 'bills', name: 'PGVCL', sub: 'electricity' },
  'torrent power': { cat: 'bills', name: 'Torrent Power', sub: 'electricity' },
  'gas authority': { cat: 'bills', name: 'Gas Agency', sub: 'gas' },
  'indane gas': { cat: 'bills', name: 'Indane Gas', sub: 'gas' },
  'bharat gas': { cat: 'bills', name: 'Bharat Gas', sub: 'gas' },
  'hp gas': { cat: 'bills', name: 'HP Gas', sub: 'gas' },
  'bbmp': { cat: 'bills', name: 'BBMP', sub: 'property_tax' },
  'mcgm': { cat: 'bills', name: 'MCGM', sub: 'property_tax' },

  // ── BANKING / EMI / CREDIT CARDS ──
  'sbi card': { cat: 'emi', name: 'SBI Card', sub: 'credit_card' },
  'hdfc credit': { cat: 'emi', name: 'HDFC Credit Card', sub: 'credit_card' },
  'icici credit': { cat: 'emi', name: 'ICICI Credit Card', sub: 'credit_card' },
  'axis credit': { cat: 'emi', name: 'Axis Credit Card', sub: 'credit_card' },
  'kotak credit': { cat: 'emi', name: 'Kotak Credit Card', sub: 'credit_card' },
  'rbl credit': { cat: 'emi', name: 'RBL Credit Card', sub: 'credit_card' },
  'indusind credit': { cat: 'emi', name: 'IndusInd Credit Card', sub: 'credit_card' },
  'yes bank credit': { cat: 'emi', name: 'Yes Bank Credit Card', sub: 'credit_card' },
  'american express': { cat: 'emi', name: 'American Express', sub: 'credit_card' },
  'bajaj finserv': { cat: 'emi', name: 'Bajaj Finserv', sub: 'bnpl' },
  'bajaj emi': { cat: 'emi', name: 'Bajaj EMI', sub: 'emi' },
  'cred': { cat: 'emi', name: 'CRED', sub: 'credit_payment' },
  'slice': { cat: 'emi', name: 'Slice', sub: 'bnpl' },
  'lazypay': { cat: 'emi', name: 'LazyPay', sub: 'bnpl' },
  'simpl': { cat: 'emi', name: 'Simpl', sub: 'bnpl' },
  'navi': { cat: 'emi', name: 'Navi', sub: 'loan' },
  'moneytap': { cat: 'emi', name: 'MoneyTap', sub: 'loan' },
  'kreditbee': { cat: 'emi', name: 'KreditBee', sub: 'loan' },
  'stashfin': { cat: 'emi', name: 'StashFin', sub: 'loan' },
  'cashe': { cat: 'emi', name: 'CASHe', sub: 'loan' },

  // ── INVESTMENTS ──
  'zerodha': { cat: 'investment', name: 'Zerodha', sub: 'brokerage' },
  'groww': { cat: 'investment', name: 'Groww', sub: 'mf_stocks' },
  'angel one': { cat: 'investment', name: 'Angel One', sub: 'brokerage' },
  'angel broking': { cat: 'investment', name: 'Angel One', sub: 'brokerage' },
  'upstox': { cat: 'investment', name: 'Upstox', sub: 'brokerage' },
  '5paisa': { cat: 'investment', name: '5paisa', sub: 'brokerage' },
  'icici direct': { cat: 'investment', name: 'ICICI Direct', sub: 'brokerage' },
  'hdfc securities': { cat: 'investment', name: 'HDFC Securities', sub: 'brokerage' },
  'kotak securities': { cat: 'investment', name: 'Kotak Securities', sub: 'brokerage' },
  'motilal oswal': { cat: 'investment', name: 'Motilal Oswal', sub: 'brokerage' },
  'sharekhan': { cat: 'investment', name: 'Sharekhan', sub: 'brokerage' },
  'coinswitch': { cat: 'investment', name: 'CoinSwitch', sub: 'crypto' },
  'wazirx': { cat: 'investment', name: 'WazirX', sub: 'crypto' },
  'kuvera': { cat: 'investment', name: 'Kuvera', sub: 'mf' },
  'scripbox': { cat: 'investment', name: 'Scripbox', sub: 'mf' },
  'paytm money': { cat: 'investment', name: 'Paytm Money', sub: 'mf' },
  'et money': { cat: 'investment', name: 'ET Money', sub: 'mf' },
  'niyo': { cat: 'investment', name: 'Niyo', sub: 'fintech' },

  // ── EDUCATION ──
  'byjus': { cat: 'education', name: "BYJU'S", sub: 'edtech' },
  "byju's": { cat: 'education', name: "BYJU'S", sub: 'edtech' },
  'unacademy': { cat: 'education', name: 'Unacademy', sub: 'edtech' },
  'vedantu': { cat: 'education', name: 'Vedantu', sub: 'edtech' },
  'coursera': { cat: 'education', name: 'Coursera', sub: 'mooc' },
  'udemy': { cat: 'education', name: 'Udemy', sub: 'mooc' },
  'skillshare': { cat: 'education', name: 'Skillshare', sub: 'mooc' },
  'linkedin learning': { cat: 'education', name: 'LinkedIn Learning', sub: 'professional' },
  'great learning': { cat: 'education', name: 'Great Learning', sub: 'edtech' },
  'upgrad': { cat: 'education', name: 'upGrad', sub: 'edtech' },
  'whitehat jr': { cat: 'education', name: 'WhiteHat Jr', sub: 'edtech' },

  // ── INSURANCE ──
  'lic': { cat: 'bills', name: 'LIC', sub: 'insurance' },
  'star health': { cat: 'bills', name: 'Star Health', sub: 'health_insurance' },
  'hdfc life': { cat: 'bills', name: 'HDFC Life', sub: 'insurance' },
  'sbi life': { cat: 'bills', name: 'SBI Life', sub: 'insurance' },
  'icici lombard': { cat: 'bills', name: 'ICICI Lombard', sub: 'general_insurance' },
  'new india': { cat: 'bills', name: 'New India Assurance', sub: 'insurance' },
  'policybazaar': { cat: 'bills', name: 'PolicyBazaar', sub: 'insurance' },
  'go digit': { cat: 'bills', name: 'Go Digit', sub: 'insurance' },

  // ── MUTUAL FUNDS / SIP ──
  'sip': { cat: 'investment', name: 'SIP Investment', sub: 'sip' },
  'mutual fund': { cat: 'investment', name: 'Mutual Fund', sub: 'mf' },
  'hdfc mutual fund': { cat: 'investment', name: 'HDFC MF', sub: 'mf' },
  'sbi mutual fund': { cat: 'investment', name: 'SBI MF', sub: 'mf' },
  'icici prudential': { cat: 'investment', name: 'ICICI Pru MF', sub: 'mf' },
  'axis mutual fund': { cat: 'investment', name: 'Axis MF', sub: 'mf' },
};

// ── TIER 2: UPI DOMAIN MAP ────────────────────────────────────────────────
// Maps the handle after @ in a UPI ID to a category
const UPI_DOMAIN_MAP: Record<string, { cat: string; name: string }> = {
  // Food
  'swiggy': { cat: 'food', name: 'Swiggy' },
  'zomato': { cat: 'food', name: 'Zomato' },
  'dunzo': { cat: 'food', name: 'Dunzo' },
  // Groceries
  'blinkit': { cat: 'groceries', name: 'Blinkit' },
  'zepto': { cat: 'groceries', name: 'Zepto' },
  'bigbasket': { cat: 'groceries', name: 'BigBasket' },
  'bb': { cat: 'groceries', name: 'BigBasket' },
  // Shopping
  'amazon': { cat: 'shopping', name: 'Amazon' },
  'amzn': { cat: 'shopping', name: 'Amazon' },
  'flipkart': { cat: 'shopping', name: 'Flipkart' },
  'fk': { cat: 'shopping', name: 'Flipkart' },
  'myntra': { cat: 'shopping', name: 'Myntra' },
  'ajio': { cat: 'shopping', name: 'Ajio' },
  'nykaa': { cat: 'shopping', name: 'Nykaa' },
  'meesho': { cat: 'shopping', name: 'Meesho' },
  // Travel
  'irctc': { cat: 'travel', name: 'IRCTC' },
  'ola': { cat: 'travel', name: 'Ola' },
  'uber': { cat: 'travel', name: 'Uber' },
  'rapido': { cat: 'travel', name: 'Rapido' },
  'mmt': { cat: 'travel', name: 'MakeMyTrip' },
  'makemytrip': { cat: 'travel', name: 'MakeMyTrip' },
  'redbus': { cat: 'travel', name: 'redBus' },
  'oyo': { cat: 'travel', name: 'OYO' },
  // Entertainment
  'netflix': { cat: 'entertainment', name: 'Netflix' },
  'hotstar': { cat: 'entertainment', name: 'Hotstar' },
  'spotify': { cat: 'entertainment', name: 'Spotify' },
  'bookmyshow': { cat: 'entertainment', name: 'BookMyShow' },
  'pvr': { cat: 'entertainment', name: 'PVR' },
  'bms': { cat: 'entertainment', name: 'BookMyShow' },
  // Investments
  'zerodha': { cat: 'investment', name: 'Zerodha' },
  'groww': { cat: 'investment', name: 'Groww' },
  'upstox': { cat: 'investment', name: 'Upstox' },
  'angelone': { cat: 'investment', name: 'Angel One' },
  'kuvera': { cat: 'investment', name: 'Kuvera' },
  // Health
  'apollo': { cat: 'health', name: 'Apollo' },
  'medplus': { cat: 'health', name: 'MedPlus' },
  'netmeds': { cat: 'health', name: 'Netmeds' },
  '1mg': { cat: 'health', name: '1mg' },
  // Bills (payment UPI handles)
  'paytm': { cat: 'bills', name: 'Paytm' },
  'airtel': { cat: 'bills', name: 'Airtel' },
  'jio': { cat: 'bills', name: 'Jio' },
  'bsnl': { cat: 'bills', name: 'BSNL' },
  // Bank UPI handles (generic → other)
  'okaxis': { cat: 'other', name: 'Axis Bank UPI' },
  'okicici': { cat: 'other', name: 'ICICI UPI' },
  'okhdfcbank': { cat: 'other', name: 'HDFC UPI' },
  'oksbi': { cat: 'other', name: 'SBI UPI' },
  'ybl': { cat: 'other', name: 'Yes Bank UPI' },
  'ibl': { cat: 'other', name: 'IndusInd UPI' },
  'upi': { cat: 'other', name: 'UPI Transfer' },
};

// ── TIER 3: KEYWORD SCORING TABLE ─────────────────────────────────────────
const KEYWORD_SCORES: Array<{ keywords: string[]; cat: string; score: number }> = [
  { keywords: ['ecommerce', 'online shopping', 'order', 'delivered', 'cod'], cat: 'shopping', score: 0.55 },
  { keywords: ['grocery', 'vegetable', 'fruit', 'supermarket', 'hypermarket'], cat: 'groceries', score: 0.55 },
  { keywords: ['restaurant', 'biryani', 'pizza', 'burger', 'cafe', 'coffee', 'dining'], cat: 'food', score: 0.55 },
  { keywords: ['petrol', 'diesel', 'fuel', 'cng', 'gas station', 'toll', 'fastag'], cat: 'fuel', score: 0.60 },
  { keywords: ['flight', 'hotel', 'cab', 'bus', 'railway', 'ticket', 'booking'], cat: 'travel', score: 0.55 },
  { keywords: ['pharmacy', 'medicine', 'hospital', 'clinic', 'doctor', 'health'], cat: 'health', score: 0.55 },
  { keywords: ['electricity', 'power bill', 'water bill', 'broadband', 'recharge', 'postpaid'], cat: 'bills', score: 0.60 },
  { keywords: ['emi', 'loan payment', 'credit card bill', 'repayment'], cat: 'emi', score: 0.65 },
  { keywords: ['sip', 'mutual fund', 'stocks', 'investment', 'portfolio'], cat: 'investment', score: 0.60 },
  { keywords: ['course', 'tuition', 'classes', 'subscription learning'], cat: 'education', score: 0.55 },
  { keywords: ['netflix', 'ott', 'subscription', 'streaming', 'cinema', 'movie'], cat: 'entertainment', score: 0.55 },
  { keywords: ['salary', 'stipend', 'payroll', 'wages'], cat: 'salary', score: 0.85 },
  { keywords: ['transfer', 'send money', 'imps', 'neft', 'rtgs'], cat: 'transfer', score: 0.70 },
];

// ── JARO-WINKLER DISTANCE ─────────────────────────────────────────────────
function jaro(s1: string, s2: string): number {
  if (s1 === s2) return 1;
  const matchDist = Math.max(Math.floor(Math.max(s1.length, s2.length) / 2) - 1, 0);
  const s1m = new Array(s1.length).fill(false);
  const s2m = new Array(s2.length).fill(false);
  let matches = 0, transpositions = 0;
  for (let i = 0; i < s1.length; i++) {
    const start = Math.max(0, i - matchDist);
    const end = Math.min(i + matchDist + 1, s2.length);
    for (let j = start; j < end; j++) {
      if (s2m[j] || s1[i] !== s2[j]) continue;
      s1m[i] = true; s2m[j] = true; matches++; break;
    }
  }
  if (!matches) return 0;
  let k = 0;
  for (let i = 0; i < s1.length; i++) {
    if (!s1m[i]) continue;
    while (!s2m[k]) k++;
    if (s1[i] !== s2[k]) transpositions++;
    k++;
  }
  return (matches / s1.length + matches / s2.length + (matches - transpositions / 2) / matches) / 3;
}

function jaroWinkler(s1: string, s2: string): number {
  const j = jaro(s1, s2);
  let prefix = 0;
  for (let i = 0; i < Math.min(4, Math.min(s1.length, s2.length)); i++) {
    if (s1[i] === s2[i]) prefix++; else break;
  }
  return j + prefix * 0.1 * (1 - j);
}

// ── MAIN CLASSIFIER ───────────────────────────────────────────────────────
function normalize(s: string): string {
  return s.toLowerCase()
    .replace(/[_\-\/\\]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function extractUpiHandle(smsBody: string): string | null {
  const m = smsBody.match(/([a-z0-9._]+)@([a-z]+)/i);
  return m ? m[1].toLowerCase() : null;
}

export function matchMerchant(rawMerchant: string, smsBody?: string): MerchantTag {
  const norm = normalize(rawMerchant);

  // Tier 1: Exact match
  if (EXACT_MAP[norm]) {
    const m = EXACT_MAP[norm];
    return { categorySlug: m.cat, displayName: m.name, subCategory: m.sub, confidence: 1.0, matchType: 'exact' };
  }

  // Check if any exact key is contained in the normalized merchant
  for (const [key, val] of Object.entries(EXACT_MAP)) {
    if (norm.includes(key) || key.includes(norm)) {
      return { categorySlug: val.cat, displayName: val.name, subCategory: val.sub, confidence: 0.95, matchType: 'exact' };
    }
  }

  // Tier 2: UPI domain extraction
  if (smsBody) {
    const handle = extractUpiHandle(smsBody);
    if (handle && UPI_DOMAIN_MAP[handle]) {
      const u = UPI_DOMAIN_MAP[handle];
      return { categorySlug: u.cat, displayName: u.name, confidence: 0.92, matchType: 'upi_domain' };
    }
    // Also try merchant name against UPI map
    if (handle) {
      for (const [upiKey, upiVal] of Object.entries(UPI_DOMAIN_MAP)) {
        if (handle.includes(upiKey) || upiKey.includes(handle.split('.')[0])) {
          return { categorySlug: upiVal.cat, displayName: upiVal.name, confidence: 0.88, matchType: 'upi_domain' };
        }
      }
    }
  }

  // Tier 3: Fuzzy matching
  let bestScore = 0, bestKey = '';
  for (const key of Object.keys(EXACT_MAP)) {
    const score = jaroWinkler(norm, key);
    if (score > bestScore) { bestScore = score; bestKey = key; }
  }
  if (bestScore >= 0.82 && EXACT_MAP[bestKey]) {
    const m = EXACT_MAP[bestKey];
    return { categorySlug: m.cat, displayName: m.name, subCategory: m.sub, confidence: bestScore * 0.9, matchType: 'fuzzy' };
  }

  // Tier 4: Keyword scoring
  const combined = `${norm} ${(smsBody || '').toLowerCase()}`;
  let kwBestScore = 0, kwBestCat = 'other';
  for (const { keywords, cat, score } of KEYWORD_SCORES) {
    const hits = keywords.filter(kw => combined.includes(kw)).length;
    const weighted = hits * score;
    if (weighted > kwBestScore) { kwBestScore = weighted; kwBestCat = cat; }
  }
  if (kwBestScore > 0) {
    return { categorySlug: kwBestCat, displayName: rawMerchant, confidence: Math.min(kwBestScore, 0.65), matchType: 'keyword' };
  }

  return { categorySlug: 'other', displayName: rawMerchant, confidence: 0.1, matchType: 'default' };
}
