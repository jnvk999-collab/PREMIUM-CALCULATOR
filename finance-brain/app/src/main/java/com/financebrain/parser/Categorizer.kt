package com.financebrain.parser

import com.financebrain.data.Categories
import com.financebrain.data.Direction

/** Keyword based categorisation. User corrections (MerchantRule) are applied before this. */
object Categorizer {

    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""swiggy|zomato|dominos|domino's|pizza|mcdonald|kfc|burger|starbucks|cafe|restaurant|dhaba|biryani|eatsure|faasos|box8|subway|barbeque|hotel""") to Categories.FOOD,
        Regex("""bigbasket|blinkit|zepto|dmart|d-mart|grofers|instamart|jiomart|reliance fresh|more supermarket|supermarket|grocer|kirana|fresh|milk|dairy""") to Categories.GROCERIES,
        Regex("""amazon|flipkart|myntra|ajio|meesho|nykaa|snapdeal|tatacliq|croma|reliance digital|decathlon|ikea|lifestyle|shoppers stop|zara|h&m|max fashion|pantaloon""") to Categories.SHOPPING,
        Regex("""uber|ola|rapido|irctc|redbus|makemytrip|goibibo|indigo|air india|vistara|spicejet|akasa|cleartrip|yatra|ixigo|metro|bmtc|ksrtc|msrtc|tsrtc|apsrtc|railway|toll|fastag|parking""") to Categories.TRAVEL,
        Regex("""petrol|diesel|fuel|hp pay|hpcl|bpcl|bharat petroleum|indian oil|iocl|shell|nayara|essar|ev charg""") to Categories.FUEL,
        Regex("""jio|airtel|vodafone|\bvi\b|bsnl|recharge|dth|tata sky|tataplay|dish tv|sun direct|broadband|act fiber|hathway|postpaid|prepaid""") to Categories.BILLS,
        Regex("""electricity|bescom|kseb|tneb|tangedco|apspdcl|tsspdcl|msedcl|bses|tata power|adani elec|water bill|gas bill|indane|hp gas|bharat gas|mahanagar gas|igl|pipeline gas|municipal|property tax""") to Categories.UTILITIES,
        Regex("""netflix|spotify|hotstar|disney|prime video|youtube|sony liv|zee5|jiocinema|bookmyshow|pvr|inox|cinepolis|gaana|wynk|apple\.com|google play|steam|playstation|xbox""") to Categories.ENTERTAINMENT,
        Regex("""apollo|pharm|medplus|netmeds|1mg|pharmeasy|hospital|clinic|diagnostic|lab|doctor|dental|practo|medical|health""") to Categories.HEALTH,
        Regex("""cred\b|cred club|credit card payment|card bill|cc payment|cc bill|card payment|autopay.{0,20}card|towards.{0,15}card|crd bill|cardbill|bill desk.{0,20}card|billdesk.{0,20}card""") to Categories.CARD_BILL,
        Regex("""\bemi\b|loan|bajaj fin|hdfc fin|home credit|lending|kreditbee|moneyview|navi|paysense""") to Categories.EMI,
        Regex("""\bsip\b|zerodha|groww|upstox|kuvera|\bcoin\b|mutual fund|\bmf\b|\bamc\b|\bnps\b|\bppf\b|etmoney|paytm money|angel one|angelone|icici direct|iciciprulife|hdfc sec|hdfc life|kotak sec|smallcase|indmoney|\brd\b|recurring deposit|\bfd\b|fixed deposit|term deposit|sbi mf|hdfc mf|icici pru|axis mf|nippon|mirae|parag parikh|ppfas|quant mf|uti mf|kotak mf|dsp mf|motilal|bse ltd|bsestarmf|nse|cams|kfintech|karvy|bsestar|indian clearing|icclbse|\bsgb\b|sovereign gold|gold bond|dhan\b|5paisa|nse clearing|nsccl|clearing corp|groww pay|groww invest|nextbillion|zerodha broking|rainmatter|sharekhan|geojit|fyers|lic\b|life insurance|ulip|elss|ncd\b|bond\b""") to Categories.INVESTMENT,
        Regex("""rent\b|landlord|nobroker|housing|nestaway|pg\s""") to Categories.RENT,
        Regex("""school|college|university|tuition|coaching|udemy|coursera|byju|unacademy|vedantu|fees|exam""") to Categories.EDUCATION,
        Regex("""atm|cash wdl|cash withdrawal""") to Categories.CASH,
        Regex("""salary|sal cr|payroll|wages|stipend""") to Categories.SALARY,
        Regex("""refund|reversal|cashback""") to Categories.REFUND,
    )

    fun categorize(counterparty: String, channel: String, direction: Direction, rawText: String?): String {
        if (channel == "INVEST") return Categories.INVESTMENT
        val hay = (counterparty + " " + (rawText ?: "")).lowercase()
        for ((re, cat) in rules) {
            if (re.containsMatchIn(hay)) {
                if (direction == Direction.CREDIT && cat !in Categories.income) return Categories.REFUND.takeIf { hay.contains("refund") } ?: Categories.INCOME
                return cat
            }
        }
        if (channel == "ATM") return Categories.CASH
        return if (direction == Direction.CREDIT) {
            if (channel in listOf("NEFT", "IMPS", "RTGS")) Categories.INCOME else Categories.TRANSFER
        } else {
            if (looksLikePerson(counterparty)) Categories.TRANSFER else Categories.OTHER
        }
    }

    /** A UPI handle or a plain two-or-three word name with no business words reads as a person. */
    fun looksLikePerson(name: String): Boolean {
        val n = name.lowercase()
        if (n.contains('@')) {
            val local = n.substringBefore('@')
            return local.any { it.isDigit() } || local.length <= 12
        }
        val words = n.split(" ").filter { it.isNotBlank() }
        val business = Regex("""pvt|ltd|llp|store|mart|shop|traders|enterprises|services|technologies|solutions|india|corp|inc|co\b|agency|bazaar|centre|center|foods|motors|petrol|pay|\d""")
        return words.size in 1..3 && !business.containsMatchIn(n)
    }

    fun merchantKey(counterparty: String): String =
        counterparty.lowercase().replace(Regex("""[^a-z0-9@]"""), "").take(40)
}
