package pl.kakebo.domain

import pl.kakebo.R

// Enum names are persistent identifiers in the local database.
enum class ExpenseCategory(val label: Int) {
    NEEDS(R.string.category_needs),
    OPTIONAL(R.string.category_optional),
    HOBBY(R.string.category_hobby),
    EXTRA(R.string.category_extra);

    val subcategories: List<ExpenseSubcategory>
        get() = ExpenseSubcategory.entries.filter { it.category == this }
}

enum class ExpenseSubcategory(val category: ExpenseCategory, val label: Int, val icon: CategoryIcon) {
    FOOD(ExpenseCategory.NEEDS, R.string.sub_food, CategoryIcon.CART),
    TRANSPORT(ExpenseCategory.NEEDS, R.string.sub_transport, CategoryIcon.TRANSPORT),
    HEALTH_MEDICINE(ExpenseCategory.NEEDS, R.string.sub_health_medicine, CategoryIcon.MEDICINE),
    HYGIENE(ExpenseCategory.NEEDS, R.string.sub_hygiene, CategoryIcon.SPARKLES),
    CHILDREN(ExpenseCategory.NEEDS, R.string.sub_children, CategoryIcon.CHILDREN),
    PETS(ExpenseCategory.NEEDS, R.string.sub_pets, CategoryIcon.ANIMALS),
    BASIC_CLOTHING(ExpenseCategory.NEEDS, R.string.sub_basic_clothing, CategoryIcon.BAG),
    DINING(ExpenseCategory.OPTIONAL, R.string.sub_dining, CategoryIcon.CAKE),
    CLOTHING(ExpenseCategory.OPTIONAL, R.string.sub_clothing, CategoryIcon.BAG),
    COSMETICS(ExpenseCategory.OPTIONAL, R.string.sub_cosmetics, CategoryIcon.HAIRCUT),
    DECORATION(ExpenseCategory.OPTIONAL, R.string.sub_decoration, CategoryIcon.HOME),
    GADGETS(ExpenseCategory.OPTIONAL, R.string.sub_gadgets, CategoryIcon.COMPUTER),
    TRAVEL(ExpenseCategory.OPTIONAL, R.string.sub_travel, CategoryIcon.TRAVEL),
    BOOKS(ExpenseCategory.HOBBY, R.string.sub_books, CategoryIcon.BOOK),
    PERFORMANCES(ExpenseCategory.HOBBY, R.string.sub_performances, CategoryIcon.TICKET),
    EVENTS(ExpenseCategory.HOBBY, R.string.sub_events, CategoryIcon.PLACES),
    HOBBY_ACTIVITIES(ExpenseCategory.HOBBY, R.string.sub_hobby_activities, CategoryIcon.ART),
    GAMES(ExpenseCategory.HOBBY, R.string.sub_games, CategoryIcon.GAMES),
    SPORT(ExpenseCategory.HOBBY, R.string.sub_sport, CategoryIcon.SPORT),
    MEDIA(ExpenseCategory.HOBBY, R.string.sub_media, CategoryIcon.FILM),
    REPAIRS(ExpenseCategory.EXTRA, R.string.sub_repairs, CategoryIcon.REPAIRS),
    EMERGENCY_HEALTH(ExpenseCategory.EXTRA, R.string.sub_emergency_health, CategoryIcon.HEALTH),
    REPLACEMENT(ExpenseCategory.EXTRA, R.string.sub_replacement, CategoryIcon.PACKAGES),
    EXTRA_OTHER(ExpenseCategory.EXTRA, R.string.sub_unexpected, CategoryIcon.WALLET),
}
