package pl.kakebo.domain

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import pl.kakebo.R

/** Stable names are persisted locally; drawable resource IDs are never stored. */
enum class CategoryIcon(@DrawableRes val drawable: Int, @StringRes val label: Int) {
    WALLET(R.drawable.ic_hero_wallet, R.string.icon_wallet),
    CART(R.drawable.ic_hero_shopping_cart, R.string.icon_cart),
    BAG(R.drawable.ic_hero_shopping_bag, R.string.icon_bag),
    BOOK(R.drawable.ic_hero_book_open, R.string.icon_book),
    MUSIC(R.drawable.ic_hero_musical_note, R.string.icon_music),
    CAKE(R.drawable.ic_hero_cake, R.string.icon_cake),
    CAMERA(R.drawable.ic_hero_camera, R.string.icon_camera),
    SPARKLES(R.drawable.ic_hero_sparkles, R.string.icon_sparkles),
    HOME(R.drawable.ic_hero_home, R.string.icon_home),
    SHOP(R.drawable.ic_hero_building_storefront, R.string.icon_shop),
    TRANSPORT(R.drawable.ic_hero_truck, R.string.icon_transport),
    TICKET(R.drawable.ic_hero_ticket, R.string.icon_ticket),
    TRAVEL(R.drawable.ic_hero_globe_alt, R.string.icon_travel),
    HEALTH(R.drawable.ic_hero_heart, R.string.icon_health),
    MEDICINE(R.drawable.ic_hero_beaker, R.string.icon_medicine),
    CHILDREN(R.drawable.ic_hero_face_smile, R.string.icon_children),
    FAMILY(R.drawable.ic_hero_user_group, R.string.icon_family),
    SPORT(R.drawable.ic_hero_trophy, R.string.icon_sport),
    RECREATION(R.drawable.ic_hero_lifebuoy, R.string.icon_recreation),
    GIFTS(R.drawable.ic_hero_gift, R.string.icon_gifts),
    PHONE(R.drawable.ic_hero_device_phone_mobile, R.string.icon_phone),
    COMPUTER(R.drawable.ic_hero_computer_desktop, R.string.icon_computer),
    REPAIRS(R.drawable.ic_hero_wrench_screwdriver, R.string.icon_repairs),
    ELECTRICITY(R.drawable.ic_hero_bolt, R.string.icon_electricity),
    HEATING(R.drawable.ic_hero_fire, R.string.icon_heating),
    EDUCATION(R.drawable.ic_hero_academic_cap, R.string.icon_education),
    WORK(R.drawable.ic_hero_briefcase, R.string.icon_work),
    BANK(R.drawable.ic_hero_building_library, R.string.icon_bank),
    CASH(R.drawable.ic_hero_banknotes, R.string.icon_cash),
    CARD(R.drawable.ic_hero_credit_card, R.string.icon_card),
    DISCOUNTS(R.drawable.ic_hero_receipt_percent, R.string.icon_discounts),
    FILM(R.drawable.ic_hero_film, R.string.icon_film),
    TV(R.drawable.ic_hero_tv, R.string.icon_tv),
    GAMES(R.drawable.ic_hero_puzzle_piece, R.string.icon_games),
    ART(R.drawable.ic_hero_paint_brush, R.string.icon_art),
    HAIRCUT(R.drawable.ic_hero_scissors, R.string.icon_haircut),
    HOLIDAYS(R.drawable.ic_hero_sun, R.string.icon_holidays),
    PLACES(R.drawable.ic_hero_map_pin, R.string.icon_places),
    PACKAGES(R.drawable.ic_hero_cube, R.string.icon_packages),
    ANIMALS(R.drawable.ic_hero_bug_ant, R.string.icon_animals);

    companion object {
        fun fromId(id: String?): CategoryIcon = entries.find { it.name == id } ?: WALLET
    }
}
