package com.futo.futopay

import com.futo.futopay.R

class PaymentConfigurations {
    object PolarConfig {
        const val ORG_SLUG = "grayjay"
        const val PRODUCT_ID = "2f6386ae-6116-409b-941f-b0de6985a01d"
        const val PRODUCT_SLUG = "grayjay-license"
        const val LICENSE_TERM = "Perpetual license"
        const val PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp8BmschIl4hA8Rzt1Mg2aaVBoUnQN+NKQ984C1pbKvYk7t+LbnWG1vm5NdiIl0ZXfOga9WiONcuGYC2/e30jv3YlBO8DY+kz639J0FxsGjeULbEAG+W0iPlLgtyJ6ttq7rAv3ICZeRRTlxYqiHzBHKrnqHpGsY2oKxZcNYWfkozA2kE2llqn0s7WzRr1lzOzbW1DFU5616ONMwWebup7zDJmlflOUtE36dTZBy0AIKLPJH9q+UZD4Qu6MKUm+VHMYjMWtQFfodjTbCqJybsVexwV+ULBp2xifZp/djxlrpG+xqVpu7Ks/rwkBczCoM/Sagl6XdeqeZAt6v6hUzUVOwIDAQAB"
    }

    object PolarConfigTesting {
        const val ORG_SLUG = "futo-test-sandbox"
        const val PRODUCT_ID = "futo-test-sandbox-product"
        const val PRODUCT_SLUG = "futo-test-sandbox-product"
    }

    companion object {

        val PAYMENT_METHODS = listOf(
            PaymentMethodDescriptor("polar", R.drawable.polar, R.string.polar,
                R.string.polar_description),
            PaymentMethodDescriptor("_", R.drawable.ic_construction, R.string.mobile_carrier_methods,
                R.string.under_construction, true),
            PaymentMethodDescriptor("_", R.drawable.ic_construction, R.string.crypto_currency_payment,
                R.string.under_construction, true)
        );

        val COUNTRIES = listOf(
            CountryDescriptor("AE", "United Arab Emirates", "الإمارات العربية المتحدة", "aed", "ae"),
            CountryDescriptor("AG", "Antigua & Barbuda", "Antigua & Barbuda", "xcd", "ag"),
            CountryDescriptor("AL", "Albania", "Shqipëri", "all", "al"),
            CountryDescriptor("AM", "Armenia", "Հայաստան", "amd", "am"),
            CountryDescriptor("AO", "Angola", "Angóla", "aoa", "ao"),
            CountryDescriptor("AR", "Argentina", "Argentina", "ars", "ar"),
            CountryDescriptor("AT", "Austria", "Österreich", "eur", "at"),
            CountryDescriptor("AU", "Australia", "Australia", "aud", "au"),
            CountryDescriptor("AZ", "Azerbaijan", "Азәрбајҹан", "azn", "az"),
            CountryDescriptor("BA", "Bosnia & Herzegovina", "Босна и Херцеговина", "bam", "ba"),
            CountryDescriptor("BD", "Bangladesh", "বাংলাদেশ", "bdt", "bd"),
            CountryDescriptor("BE", "Belgium", "Belgien", "eur", "be"),
            CountryDescriptor("BG", "Bulgaria", "България", "bgn", "bg"),
            CountryDescriptor("BH", "Bahrain", "البحرين", "bhd", "bh"),
            CountryDescriptor("BJ", "Benin", "Bénin", "xof", "bj"),
            CountryDescriptor("BN", "Brunei", "Brunei", "bnd", "bn"),
            CountryDescriptor("BO", "Bolivia", "Bolivia", "bob", "bo"),
            CountryDescriptor("BR", "Brazil", "Brasil", "brl", "br"),
            CountryDescriptor("BS", "Bahamas", "Bahamas", "bsd", "bs"),
            CountryDescriptor("BT", "Bhutan", "འབྲུག", "btn", "bt"),
            CountryDescriptor("BW", "Botswana", "Botswana", "bwp", "bw"),
            CountryDescriptor("CA", "Canada", "Canada", "cad", "ca"),
            CountryDescriptor("CH", "Switzerland", "Schweiz", "chf", "ch"),
            CountryDescriptor("CI", "Côte d’Ivoire", "Côte d’Ivoire", "xof", "ci"),
            CountryDescriptor("CL", "Chile", "Chile", "clp", "cl"),
            CountryDescriptor("CO", "Colombia", "Colombia", "cop", "co"),
            CountryDescriptor("CR", "Costa Rica", "Costa Rica", "crc", "cr"),
            CountryDescriptor("CY", "Cyprus", "Κύπρος", "eur", "cy"),
            CountryDescriptor("CZ", "Czechia", "Česko", "czk", "cz"),
            CountryDescriptor("DE", "Germany", "Deutschland", "eur", "de"),
            CountryDescriptor("DK", "Denmark", "Danmark", "dkk", "dk"),
            CountryDescriptor("DO", "Dominican Republic", "República Dominicana", "dop", "do"),
            CountryDescriptor("DZ", "Algeria", "الجزائر", "dzd", "dz"),
            CountryDescriptor("EC", "Ecuador", "Ecuador", "usd", "ec"),
            CountryDescriptor("EE", "Estonia", "Eesti", "eur", "ee"),
            CountryDescriptor("EG", "Egypt", "مصر", "egp", "eg"),
            CountryDescriptor("ES", "Spain", "España", "eur", "es"),
            CountryDescriptor("ET", "Ethiopia", "ኢትዮጵያ", "etb", "et"),
            CountryDescriptor("FI", "Finland", "Finland", "eur", "fi"),
            CountryDescriptor("FR", "France", "Frañs", "eur", "fr"),
            CountryDescriptor("GA", "Gabon", "Gabon", "xaf", "ga"),
            CountryDescriptor("GB", "United Kingdom", "United Kingdom", "gbp", "gb"),
            CountryDescriptor("GH", "Ghana", "Gaana", "ghs", "gh"),
            CountryDescriptor("GI", "Gibraltar", "Gibraltar", "gbp", "gi"),
            CountryDescriptor("GM", "Gambia", "Gambia", "gmd", "gm"),
            CountryDescriptor("GR", "Greece", "Ελλάδα", "eur", "gr"),
            CountryDescriptor("GT", "Guatemala", "Guatemala", "gtq", "gt"),
            CountryDescriptor("GY", "Guyana", "Guyana", "gyd", "gy"),
            CountryDescriptor("HK", "Hong Kong SAR China", "Hong Kong SAR China", "hkd", "hk"),
            CountryDescriptor("HR", "Croatia", "Hrvatska", "eur", "hr"),
            CountryDescriptor("HU", "Hungary", "Magyarország", "huf", "hu"),
            CountryDescriptor("ID", "Indonesia", "Indonesia", "idr", "id"),
            CountryDescriptor("IE", "Ireland", "Éire", "eur", "ie"),
            CountryDescriptor("IL", "Israel", "إسرائيل", "ils", "il"),
            CountryDescriptor("IN", "India", "ভাৰত", "inr", "in"),
            CountryDescriptor("IS", "Iceland", "Ísland", "eur", "is"),
            CountryDescriptor("IT", "Italy", "Itàlia", "eur", "it"),
            CountryDescriptor("JM", "Jamaica", "Jamaica", "jmd", "jm"),
            CountryDescriptor("JO", "Jordan", "الأردن", "jod", "jo"),
            CountryDescriptor("JP", "Japan", "日本", "jpy", "jp"),
            CountryDescriptor("KE", "Kenya", "Kenya", "kes", "ke"),
            CountryDescriptor("KH", "Cambodia", "កម្ពុជា", "khr", "kh"),
            CountryDescriptor("KR", "South Korea", "대한민국", "krw", "kr"),
            CountryDescriptor("KW", "Kuwait", "الكويت", "kwd", "kw"),
            CountryDescriptor("KZ", "Kazakhstan", "Қазақстан", "kzt", "kz"),
            CountryDescriptor("LA", "Laos", "ລາວ", "lak", "la"),
            CountryDescriptor("LC", "St. Lucia", "St. Lucia", "xcd", "lc"),
            CountryDescriptor("LI", "Liechtenstein", "Liechtenstein", "chf", "li"),
            CountryDescriptor("LK", "Sri Lanka", "ශ්‍රී ලංකාව", "lkr", "lk"),
            CountryDescriptor("LT", "Lithuania", "Lietuva", "eur", "lt"),
            CountryDescriptor("LU", "Luxembourg", "Luxemburg", "eur", "lu"),
            CountryDescriptor("LV", "Latvia", "Latvija", "eur", "lv"),
            CountryDescriptor("MA", "Morocco", "المغرب", "mad", "ma"),
            CountryDescriptor("MC", "Monaco", "Monaco", "eur", "mc"),
            CountryDescriptor("MD", "Moldova", "Republica Moldova", "mdl", "md"),
            CountryDescriptor("MG", "Madagascar", "Madagascar", "mga", "mg"),
            CountryDescriptor("MK", "North Macedonia", "Северна Македонија", "mkd", "mk"),
            CountryDescriptor("MN", "Mongolia", "Монгол", "mnt", "mn"),
            CountryDescriptor("MO", "Macao SAR China", "Macao SAR China", "mop", "mo"),
            CountryDescriptor("MT", "Malta", "Malta", "eur", "mt"),
            CountryDescriptor("MU", "Mauritius", "Mauritius", "mur", "mu"),
            CountryDescriptor("MX", "Mexico", "México", "mxn", "mx"),
            CountryDescriptor("MY", "Malaysia", "Malaysia", "myr", "my"),
            CountryDescriptor("MZ", "Mozambique", "Umozambiki", "mzn", "mz"),
            CountryDescriptor("NA", "Namibia", "Namibië", "nad", "na"),
            CountryDescriptor("NE", "Niger", "Nižer", "xof", "ne"),
            CountryDescriptor("NG", "Nigeria", "Nigeria", "ngn", "ng"),
            CountryDescriptor("NL", "Netherlands", "Netherlands", "eur", "nl"),
            CountryDescriptor("NO", "Norway", "Norge", "nok", "no"),
            CountryDescriptor("NZ", "New Zealand", "New Zealand", "nzd", "nz"),
            CountryDescriptor("OM", "Oman", "عُمان", "omr", "om"),
            CountryDescriptor("PA", "Panama", "Panamá", "usd", "pa"),
            CountryDescriptor("PE", "Peru", "Perú", "pen", "pe"),
            CountryDescriptor("PH", "Philippines", "Pilipinas", "php", "ph"),
            CountryDescriptor("PK", "Pakistan", "Pakistan", "pkr", "pk"),
            CountryDescriptor("PL", "Poland", "Polska", "pln", "pl"),
            CountryDescriptor("PT", "Portugal", "Portugal", "eur", "pt"),
            CountryDescriptor("PY", "Paraguay", "Paraguay", "pyg", "py"),
            CountryDescriptor("QA", "Qatar", "قطر", "qar", "qa"),
            CountryDescriptor("RO", "Romania", "România", "ron", "ro"),
            CountryDescriptor("RS", "Serbia", "Србија", "rsd", "rs"),
            CountryDescriptor("RU", "Russia", "Российская Федерация", "rub", "ru"),
            CountryDescriptor("RW", "Rwanda", "Rwanda", "rwf", "rw"),
            CountryDescriptor("SA", "Saudi Arabia", "المملكة العربية السعودية", "sar", "sa"),
            CountryDescriptor("SE", "Sweden", "Sweden", "sek", "se"),
            CountryDescriptor("SG", "Singapore", "Singapore", "sgd", "sg"),
            CountryDescriptor("SI", "Slovenia", "Slovenia", "eur", "si"),
            CountryDescriptor("SK", "Slovakia", "Slovensko", "eur", "sk"),
            CountryDescriptor("SM", "San Marino", "San Marino", "eur", "sm"),
            CountryDescriptor("SN", "Senegal", "Senegal", "xof", "sn"),
            CountryDescriptor("SV", "El Salvador", "El Salvador", "usd", "sv"),
            CountryDescriptor("TH", "Thailand", "ไทย", "thb", "th"),
            CountryDescriptor("TN", "Tunisia", "تونس", "tnd", "tn"),
            CountryDescriptor("TR", "Turkey", "Türkiye", "try", "tr"),
            CountryDescriptor("TT", "Trinidad & Tobago", "Trinidad & Tobago", "ttd", "tt"),
            CountryDescriptor("TW", "Taiwan", "台灣", "twd", "tw"),
            CountryDescriptor("TZ", "Tanzania", "Tadhania", "tzs", "tz"),
            CountryDescriptor("US", "United States", "United States", "usd", "us"),
            CountryDescriptor("UY", "Uruguay", "Uruguay", "uyu", "uy"),
            CountryDescriptor("UZ", "Uzbekistan", "Ўзбекистон", "uzs", "uz"),
            CountryDescriptor("VN", "Vietnam", "Việt Nam", "vnd", "vn"),
            CountryDescriptor("ZA", "South Africa", "Suid-Afrika", "zar", "za")
        );
        val CURRENCIES = listOf(
            CurrencyDescriptor("aed", "United Arab Emirates Dirham", "درهم إماراتي", "د.إ.‏", "ae"), //AE
            CurrencyDescriptor("xcd", "East Caribbean Dollar", "East Caribbean Dollar", "\$", "ag"), //AG, LC
            CurrencyDescriptor("all", "Albanian Lek", "Leku shqiptar", "Lekë", "al"), //AL
            CurrencyDescriptor("amd", "Armenian Dram", "հայկական դրամ", "֏", "am"), //AM
            CurrencyDescriptor("aoa", "Angolan Kwanza", "Kwanza ya Angóla", "Kz", "ao"), //AO
            CurrencyDescriptor("ars", "Argentine Peso", "peso argentino", "\$", "ar"), //AR
            CurrencyDescriptor("eur", "Euro", "Euro", "€", "eu"), //AT, BE, CY, DE, EE, ES, FI, FR, GR, HR, IE, IS, IT, LT, LU, LV, MC, MT, NL, PT, SI, SK, SM
            CurrencyDescriptor("aud", "Australian Dollar", "Australian Dollar", "\$", "au"), //AU
            CurrencyDescriptor("azn", "Azerbaijani Manat", "AZN", "₼", "az"), //AZ
            CurrencyDescriptor("bam", "Bosnia-Herzegovina Convertible Mark", "Конвертибилна марка", "КМ", "ba"), //BA
            CurrencyDescriptor("bdt", "Bangladeshi Taka", "বাংলাদেশী টাকা", "৳", "bd"), //BD
            CurrencyDescriptor("bgn", "Bulgarian Lev", "Български лев", "лв.", "bg"), //BG
            CurrencyDescriptor("bhd", "Bahraini Dinar", "دينار بحريني", "د.ب.‏", "bh"), //BH
            CurrencyDescriptor("xof", "West African CFA Franc", "franc CFA (BCEAO)", "F CFA", "bj"), //BJ, CI, NE, SN
            CurrencyDescriptor("bnd", "Brunei Dollar", "Dolar Brunei", "\$", "bn"), //BN
            CurrencyDescriptor("bob", "Bolivian Boliviano", "boliviano", "Bs", "bo"), //BO
            CurrencyDescriptor("brl", "Brazilian Real", "real brasileño", "R\$", "br"), //BR
            CurrencyDescriptor("bsd", "Bahamian Dollar", "Bahamian Dollar", "\$", "bs"), //BS
            CurrencyDescriptor("btn", "Bhutanese Ngultrum", "དངུལ་ཀྲམ", "Nu.", "bt"), //BT
            CurrencyDescriptor("bwp", "Botswanan Pula", "Botswanan Pula", "P", "bw"), //BW
            CurrencyDescriptor("cad", "Canadian Dollar", "Canadian Dollar", "\$", "ca"), //CA
            CurrencyDescriptor("chf", "Swiss Franc", "Schweizer Franken", "CHF", "ch"), //CH, LI
            CurrencyDescriptor("clp", "Chilean Peso", "Peso chileno", "\$", "cl"), //CL
            CurrencyDescriptor("cop", "Colombian Peso", "peso colombiano", "\$", "co"), //CO
            CurrencyDescriptor("crc", "Costa Rican Colón", "colón costarricense", "₡", "cr"), //CR
            CurrencyDescriptor("czk", "Czech Koruna", "česká koruna", "Kč", "cz"), //CZ
            CurrencyDescriptor("dkk", "Danish Krone", "dansk krone", "kr.", "dk"), //DK
            CurrencyDescriptor("dop", "Dominican Peso", "peso dominicano", "RD\$", "do"), //DO
            CurrencyDescriptor("dzd", "Algerian Dinar", "دينار جزائري", "د.ج.‏", "dz"), //DZ
            CurrencyDescriptor("usd", "United States Dollar", "dólar estadounidense", "\$", "us"), //EC, PA, SV, US
            CurrencyDescriptor("egp", "Egyptian Pound", "جنيه مصري", "ج.م.‏", "eg"), //EG
            CurrencyDescriptor("etb", "Ethiopian Birr", "የኢትዮጵያ ብር", "ብር", "et"), //ET
            CurrencyDescriptor("xaf", "Central African CFA Franc", "franc CFA (BEAC)", "FCFA", "ga"), //GA
            CurrencyDescriptor("gbp", "British Pound", "Punt Prydain", "£", "gb"), //GB, GI
            CurrencyDescriptor("ghs", "Ghanaian Cedi", "Ghana Sidi", "GH₵", "gh"), //GH
            CurrencyDescriptor("gmd", "Gambian Dalasi", "Gambian Dalasi", "D", "gm"), //GM
            CurrencyDescriptor("gtq", "Guatemalan Quetzal", "quetzal", "Q", "gt"), //GT
            CurrencyDescriptor("gyd", "Guyanaese Dollar", "Guyanaese Dollar", "\$", "gy"), //GY
            CurrencyDescriptor("hkd", "Hong Kong Dollar", "Hong Kong Dollar", "HK\$", "hk"), //HK
            CurrencyDescriptor("huf", "Hungarian Forint", "magyar forint", "Ft", "hu"), //HU
            CurrencyDescriptor("idr", "Indonesian Rupiah", "Rupiah Indonesia", "Rp", "id"), //ID
            CurrencyDescriptor("ils", "Israeli New Shekel", "شيكل إسرائيلي جديد", "₪", "il"), //IL
            CurrencyDescriptor("inr", "Indian Rupee", "ভাৰতীয় ৰুপী", "₹", "in"), //IN
            CurrencyDescriptor("jmd", "Jamaican Dollar", "Jamaican Dollar", "\$", "jm"), //JM
            CurrencyDescriptor("jod", "Jordanian Dinar", "دينار أردني", "د.أ.‏", "jo"), //JO
            CurrencyDescriptor("jpy", "Japanese Yen", "日本円", "￥", "jp"), //JP
            CurrencyDescriptor("kes", "Kenyan Shilling", "Shilingi ya Kenya", "Ksh", "ke"), //KE
            CurrencyDescriptor("khr", "Cambodian Riel", "រៀល​កម្ពុជា", "៛", "kh"), //KH
            CurrencyDescriptor("krw", "South Korean Won", "대한민국 원", "₩", "kr"), //KR
            CurrencyDescriptor("kwd", "Kuwaiti Dinar", "دينار كويتي", "د.ك.‏", "kw"), //KW
            CurrencyDescriptor("kzt", "Kazakhstani Tenge", "Қазақстан теңгесі", "₸", "kz"), //KZ
            CurrencyDescriptor("lak", "Laotian Kip", "ລາວ ກີບ", "₭", "la"), //LA
            CurrencyDescriptor("lkr", "Sri Lankan Rupee", "ශ්‍රී ලංකා රුපියල", "රු.", "lk"), //LK
            CurrencyDescriptor("mad", "Moroccan Dirham", "درهم مغربي", "د.م.‏", "ma"), //MA
            CurrencyDescriptor("mdl", "Moldovan Leu", "leu moldovenesc", "L", "md"), //MD
            CurrencyDescriptor("mga", "Malagasy Ariary", "Malagasy Ariary", "Ar", "mg"), //MG
            CurrencyDescriptor("mkd", "Macedonian Denar", "Македонски денар", "ден.", "mk"), //MK
            CurrencyDescriptor("mnt", "Mongolian Tugrik", "Монгол төгрөг", "₮", "mn"), //MN
            CurrencyDescriptor("mop", "Macanese Pataca", "Macanese Pataca", "MOP\$", "mo"), //MO
            CurrencyDescriptor("mur", "Mauritian Rupee", "Mauritian Rupee", "Rs", "mu"), //MU
            CurrencyDescriptor("mxn", "Mexican Peso", "peso mexicano", "\$", "mx"), //MX
            CurrencyDescriptor("myr", "Malaysian Ringgit", "Malaysian Ringgit", "RM", "my"), //MY
            CurrencyDescriptor("mzn", "Mozambican Metical", "MZN", "MTn", "mz"), //MZ
            CurrencyDescriptor("nad", "Namibian Dollar", "Namibiese dollar", "\$", "na"), //NA
            CurrencyDescriptor("ngn", "Nigerian Naira", "Nigerian Naira", "₦", "ng"), //NG
            CurrencyDescriptor("nok", "Norwegian Krone", "norske kroner", "kr", "no"), //NO
            CurrencyDescriptor("nzd", "New Zealand Dollar", "New Zealand Dollar", "\$", "nz"), //NZ
            CurrencyDescriptor("omr", "Omani Rial", "ريال عماني", "ر.ع.‏", "om"), //OM
            CurrencyDescriptor("pen", "Peruvian Sol", "sol peruano", "S/", "pe"), //PE
            CurrencyDescriptor("php", "Philippine Peso", "Philippine Piso", "₱", "ph"), //PH
            CurrencyDescriptor("pkr", "Pakistani Rupee", "Pakistani Rupee", "Rs", "pk"), //PK
            CurrencyDescriptor("pln", "Polish Zloty", "złoty polski", "zł", "pl"), //PL
            CurrencyDescriptor("pyg", "Paraguayan Guarani", "guaraní paraguayo", "Gs.", "py"), //PY
            CurrencyDescriptor("qar", "Qatari Rial", "ريال قطري", "ر.ق.‏", "qa"), //QA
            CurrencyDescriptor("ron", "Romanian Leu", "leu românesc", "RON", "ro"), //RO
            CurrencyDescriptor("rsd", "Serbian Dinar", "српски динар", "RSD", "rs"), //RS
            CurrencyDescriptor("rub", "Russian Rubble", "Российский рубль", "RUB", "ru"), //RU
            CurrencyDescriptor("rwf", "Rwandan Franc", "Rwandan Franc", "RF", "rw"), //RW
            CurrencyDescriptor("sar", "Saudi Riyal", "ريال سعودي", "ر.س.‏", "sa"), //SA
            CurrencyDescriptor("sek", "Swedish Krona", "Swedish Krona", "kr", "se"), //SE
            CurrencyDescriptor("sgd", "Singapore Dollar", "Singapore Dollar", "\$", "sg"), //SG
            CurrencyDescriptor("thb", "Thai Baht", "บาท", "฿", "th"), //TH
            CurrencyDescriptor("tnd", "Tunisian Dinar", "دينار تونسي", "د.ت.‏", "tn"), //TN
            CurrencyDescriptor("try", "Turkish Lira", "TRY", "₺", "tr"), //TR
            CurrencyDescriptor("ttd", "Trinidad & Tobago Dollar", "Trinidad & Tobago Dollar", "\$", "tt"), //TT
            CurrencyDescriptor("twd", "New Taiwan Dollar", "新台幣", "\$", "tw"), //TW
            CurrencyDescriptor("tzs", "Tanzanian Shilling", "shilingi ya Tandhania", "TSh", "tz"), //TZ
            CurrencyDescriptor("uyu", "Uruguayan Peso", "peso uruguayo", "\$", "uy"), //UY
            CurrencyDescriptor("uzs", "Uzbekistani Som", "Ўзбекистон сўм", "сўм", "uz"), //UZ
            CurrencyDescriptor("vnd", "Vietnamese Dong", "Đồng Việt Nam", "₫", "vn"), //VN
            CurrencyDescriptor("zar", "South African Rand", "Suid-Afrikaanse rand", "R", "za"), //ZA
        );
    }

    data class PaymentMethodDescriptor(
        val id: String,
        val image: Int,
        val name: Int,
        val description: Int,
        val isDisabled: Boolean = false
    );
    data class CountryDescriptor(
        val id: String,
        val nameEnglish: String,
        val nameNative: String,
        val defaultCurrencyId: String,
        val flag: String?
    );
    data class CurrencyDescriptor(
        val id: String,
        val nameEnglish: String,
        val nameNative: String,
        val symbol: String,
        val flag: String?
    );
}