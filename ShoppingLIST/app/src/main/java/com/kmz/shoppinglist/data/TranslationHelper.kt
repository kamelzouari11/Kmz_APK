package com.kmz.shoppinglist.data

/**
 * Gestionnaire de traduction pour les noms d'articles Permet de traduire arabe/français vers
 * anglais pour la recherche d'icônes
 */
object TranslationHelper {

    /** Dictionnaire de traduction arabe -> anglais Mots courants pour les courses */
    private val arabicToEnglish =
            mapOf(
                    // Fruits
                    "تفاح" to "apple",
                    "موز" to "banana",
                    "برتقال" to "orange",
                    "فراولة" to "strawberry",
                    "عنب" to "grapes",
                    "بطيخ" to "watermelon",
                    "شمام" to "melon",
                    "خوخ" to "peach",
                    "مانجو" to "mango",
                    "أناناس" to "pineapple",
                    "كيوي" to "kiwi",
                    "ليمون" to "lemon",
                    "تمر" to "dates",
                    "تين" to "fig",
                    "رمان" to "pomegranate",

                    // Légumes
                    "طماطم" to "tomato",
                    "بصل" to "onion",
                    "ثوم" to "garlic",
                    "بطاطس" to "potato",
                    "جزر" to "carrot",
                    "خيار" to "cucumber",
                    "فلفل" to "pepper",
                    "باذنجان" to "eggplant",
                    "كوسة" to "zucchini",
                    "بروكلي" to "broccoli",
                    "سبانخ" to "spinach",
                    "خس" to "lettuce",
                    "ملفوف" to "cabbage",
                    "فاصوليا" to "beans",
                    "بازلاء" to "peas",
                    "ذرة" to "corn",

                    // Viandes
                    "لحم" to "meat",
                    "لحم بقر" to "beef",
                    "لحم غنم" to "lamb",
                    "دجاج" to "chicken",
                    "ديك رومي" to "turkey",
                    "سمك" to "fish",
                    "جمبري" to "shrimp",
                    "تونة" to "tuna",
                    "سردين" to "sardine",

                    // Produits laitiers
                    "حليب" to "milk",
                    "لبن" to "yogurt",
                    "جبن" to "cheese",
                    "زبدة" to "butter",
                    "قشطة" to "cream",
                    "بيض" to "eggs",

                    // Épicerie
                    "خبز" to "bread",
                    "أرز" to "rice",
                    "معكرونة" to "pasta",
                    "سكر" to "sugar",
                    "ملح" to "salt",
                    "زيت" to "oil",
                    "زيت زيتون" to "olive oil",
                    "طحين" to "flour",
                    "شاي" to "tea",
                    "قهوة" to "coffee",
                    "عسل" to "honey",
                    "مربى" to "jam",
                    "شوكولاتة" to "chocolate",
                    "بسكويت" to "biscuits",
                    "حبوب" to "cereal",

                    // Boissons
                    "ماء" to "water",
                    "عصير" to "juice",
                    "مشروب غازي" to "soda",
                    "كولا" to "cola",

                    // Autres
                    "صابون" to "soap",
                    "شامبو" to "shampoo",
                    "معجون أسنان" to "toothpaste",
                    "ورق" to "paper",
                    "منظف" to "detergent"
            )

    /** Dictionnaire de traduction français -> anglais */
    private val frenchToEnglish =
            mapOf(
                    // Fruits
                    "pomme" to "apple",
                    "pommes" to "apple",
                    "banane" to "banana",
                    "bananes" to "banana",
                    "orange" to "orange",
                    "oranges" to "orange",
                    "fraise" to "strawberry",
                    "fraises" to "strawberry",
                    "raisin" to "grapes",
                    "raisins" to "grapes",
                    "pastèque" to "watermelon",
                    "melon" to "melon",
                    "pêche" to "peach",
                    "pêches" to "peach",
                    "mangue" to "mango",
                    "ananas" to "pineapple",
                    "kiwi" to "kiwi",
                    "citron" to "lemon",
                    "citrons" to "lemon",
                    "dattes" to "dates",
                    "figue" to "fig",
                    "figues" to "fig",
                    "grenade" to "pomegranate",

                    // Légumes
                    "tomate" to "tomato",
                    "tomates" to "tomato",
                    "oignon" to "onion",
                    "oignons" to "onion",
                    "ail" to "garlic",
                    "pomme de terre" to "potato",
                    "pommes de terre" to "potato",
                    "patate" to "potato",
                    "patates" to "potato",
                    "carotte" to "carrot",
                    "carottes" to "carrot",
                    "concombre" to "cucumber",
                    "concombres" to "cucumber",
                    "poivron" to "pepper",
                    "poivrons" to "pepper",
                    "aubergine" to "eggplant",
                    "aubergines" to "eggplant",
                    "courgette" to "zucchini",
                    "courgettes" to "zucchini",
                    "brocoli" to "broccoli",
                    "épinard" to "spinach",
                    "épinards" to "spinach",
                    "salade" to "lettuce",
                    "laitue" to "lettuce",
                    "chou" to "cabbage",
                    "haricots" to "beans",
                    "haricot" to "beans",
                    "petits pois" to "peas",
                    "maïs" to "corn",

                    // Viandes
                    "viande" to "meat",
                    "bœuf" to "beef",
                    "agneau" to "lamb",
                    "poulet" to "chicken",
                    "dinde" to "turkey",
                    "poisson" to "fish",
                    "crevettes" to "shrimp",
                    "crevette" to "shrimp",
                    "thon" to "tuna",
                    "sardine" to "sardine",
                    "sardines" to "sardine",

                    // Produits laitiers
                    "lait" to "milk",
                    "yaourt" to "yogurt",
                    "yaourts" to "yogurt",
                    "fromage" to "cheese",
                    "beurre" to "butter",
                    "crème" to "cream",
                    "œuf" to "eggs",
                    "œufs" to "eggs",
                    "oeuf" to "eggs",
                    "oeufs" to "eggs",

                    // Épicerie
                    "pain" to "bread",
                    "riz" to "rice",
                    "pâtes" to "pasta",
                    "sucre" to "sugar",
                    "sel" to "salt",
                    "huile" to "oil",
                    "huile d'olive" to "olive oil",
                    "farine" to "flour",
                    "thé" to "tea",
                    "café" to "coffee",
                    "miel" to "honey",
                    "confiture" to "jam",
                    "chocolat" to "chocolate",
                    "biscuits" to "biscuits",
                    "biscuit" to "biscuits",
                    "céréales" to "cereal",

                    // Boissons
                    "eau" to "water",
                    "eau minérale" to "water",
                    "jus" to "juice",
                    "jus d'orange" to "juice",
                    "soda" to "soda",
                    "coca" to "cola",

                    // Autres
                    "savon" to "soap",
                    "shampooing" to "shampoo",
                    "dentifrice" to "toothpaste",
                    "papier" to "paper",
                    "papier toilette" to "toilet paper",
                    "lessive" to "detergent",

                    // Nouveaux ajouts demandés
                    "poivron vert" to "green pepper",
                    "poivron rouge" to "red pepper",
                    "eau minerale" to "mineral water",
                    "eau minérale" to "mineral water",
                    "eau de source" to "spring water",
                    "pomme de terre" to "potato",

                    // Catégories
                    "fruits" to "fruits",
                    "légumes" to "vegetables",
                    "viande" to "meat",
                    "viandes" to "meat",
                    "boissons" to "drinks",
                    "épicerie" to "grocery",
                    "boulangerie" to "bakery",
                    "surgelés" to "frozen food"
            )

    /** Détecte si le texte contient des caractères arabes */
    fun isArabic(text: String): Boolean {
        return text.any { char -> Character.UnicodeBlock.of(char) == Character.UnicodeBlock.ARABIC }
    }

    /**
     * Traduit un nom d'article vers l'anglais pour la recherche d'icônes
     * @return Le mot anglais correspondant ou le mot original si non trouvé
     */
    fun translateToEnglish(articleName: String): String {
        val normalizedName = articleName.trim().lowercase()

        // 1. Chercher d'abord une correspondance exacte (priorité haute)
        val dictionary = if (isArabic(articleName)) arabicToEnglish else frenchToEnglish
        dictionary[normalizedName]?.let {
            return it
        }

        // 2. Chercher une correspondance partielle (le dictionnaire contient une partie du nom)
        // ex: "poivron vert" contient "poivron", donc retourne "pepper" (si pas de match exact
        // avant)
        for ((source, target) in dictionary) {
            if (normalizedName.contains(source)) {
                return target
            }
        }

        // 3. Fallback: Chercher si une partie du dictionnaire est contenue dans le nom
        // (déjà géré par le point 2, mais on peut être plus précis si besoin)

        // Si pas de traduction trouvée, retourner le nom original
        return articleName
    }

    /** Génère un mot-clé de recherche pour une icône */
    fun getIconSearchKeyword(articleName: String): String {
        val translated = translateToEnglish(articleName)
        // Nettoyer le mot pour la recherche (conserver les espaces pour multi-mots)
        return translated.lowercase().replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
    }
}
