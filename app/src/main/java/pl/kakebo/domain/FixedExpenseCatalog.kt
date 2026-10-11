package pl.kakebo.domain

import androidx.annotation.StringRes
import pl.kakebo.R

enum class FixedExpenseGroup(@StringRes val label: Int, val icon: CategoryIcon) {
    HOUSING(R.string.fixed_group_housing, CategoryIcon.HOME),
    BILLS(R.string.fixed_group_bills, CategoryIcon.ELECTRICITY),
    PHONE_INTERNET(R.string.fixed_group_phone_internet, CategoryIcon.PHONE),
    INSURANCE(R.string.fixed_group_insurance, CategoryIcon.HEALTH),
    FINANCE(R.string.fixed_group_finance, CategoryIcon.BANK),
    FAMILY(R.string.fixed_group_family, CategoryIcon.FAMILY),
    HEALTHCARE(R.string.fixed_group_healthcare, CategoryIcon.MEDICINE),
    TRANSPORT(R.string.fixed_group_transport, CategoryIcon.TRANSPORT),
    SUBSCRIPTIONS(R.string.fixed_group_subscriptions, CategoryIcon.TV),
    OTHER(R.string.fixed_group_other, CategoryIcon.SPARKLES),
}

enum class FixedExpensePreset(
    @StringRes val label: Int,
    val group: FixedExpenseGroup,
    val icon: CategoryIcon,
    val custom: Boolean = false,
) {
    ADMIN_RENT(R.string.fixed_admin_rent, FixedExpenseGroup.HOUSING, CategoryIcon.HOME),
    RENT(R.string.fixed_rent, FixedExpenseGroup.HOUSING, CategoryIcon.CASH),
    MORTGAGE(R.string.fixed_mortgage, FixedExpenseGroup.HOUSING, CategoryIcon.BANK),
    RENOVATION_FUND(R.string.fixed_renovation_fund, FixedExpenseGroup.HOUSING, CategoryIcon.REPAIRS),
    GARAGE(R.string.fixed_garage, FixedExpenseGroup.HOUSING, CategoryIcon.TRANSPORT),

    ELECTRICITY(R.string.fixed_electricity, FixedExpenseGroup.BILLS, CategoryIcon.ELECTRICITY),
    GAS(R.string.fixed_gas, FixedExpenseGroup.BILLS, CategoryIcon.HEATING),
    WATER(R.string.fixed_water, FixedExpenseGroup.BILLS, CategoryIcon.RECREATION),
    HEATING(R.string.fixed_heating, FixedExpenseGroup.BILLS, CategoryIcon.HEATING),
    WASTE(R.string.fixed_waste, FixedExpenseGroup.BILLS, CategoryIcon.PACKAGES),

    MOBILE(R.string.fixed_mobile, FixedExpenseGroup.PHONE_INTERNET, CategoryIcon.PHONE),
    INTERNET(R.string.fixed_internet, FixedExpenseGroup.PHONE_INTERNET, CategoryIcon.COMPUTER),
    TELEVISION(R.string.fixed_television, FixedExpenseGroup.PHONE_INTERNET, CategoryIcon.TV),

    HOME_INSURANCE(R.string.fixed_home_insurance, FixedExpenseGroup.INSURANCE, CategoryIcon.HOME),
    CAR_INSURANCE(R.string.fixed_car_insurance, FixedExpenseGroup.INSURANCE, CategoryIcon.TRANSPORT),
    LIFE_INSURANCE(R.string.fixed_life_insurance, FixedExpenseGroup.INSURANCE, CategoryIcon.FAMILY),
    HEALTH_INSURANCE(R.string.fixed_health_insurance, FixedExpenseGroup.INSURANCE, CategoryIcon.HEALTH),

    CASH_LOAN(R.string.fixed_cash_loan, FixedExpenseGroup.FINANCE, CategoryIcon.CASH),
    CAR_INSTALLMENT(R.string.fixed_car_installment, FixedExpenseGroup.FINANCE, CategoryIcon.TRANSPORT),
    ACCOUNT_FEES(R.string.fixed_account_fees, FixedExpenseGroup.FINANCE, CategoryIcon.CARD),

    NURSERY(R.string.fixed_nursery, FixedExpenseGroup.FAMILY, CategoryIcon.CHILDREN),
    TUITION(R.string.fixed_tuition, FixedExpenseGroup.FAMILY, CategoryIcon.EDUCATION),
    CHILD_ACTIVITIES(R.string.fixed_child_activities, FixedExpenseGroup.FAMILY, CategoryIcon.SPORT),
    ALIMONY(R.string.fixed_alimony, FixedExpenseGroup.FAMILY, CategoryIcon.FAMILY),

    MEDICAL_PLAN(R.string.fixed_medical_plan, FixedExpenseGroup.HEALTHCARE, CategoryIcon.HEALTH),
    THERAPY(R.string.fixed_therapy, FixedExpenseGroup.HEALTHCARE, CategoryIcon.RECREATION),
    REGULAR_MEDICINE(R.string.fixed_regular_medicine, FixedExpenseGroup.HEALTHCARE, CategoryIcon.MEDICINE),

    MONTHLY_TICKET(R.string.fixed_monthly_ticket, FixedExpenseGroup.TRANSPORT, CategoryIcon.TICKET),
    LEASING(R.string.fixed_leasing, FixedExpenseGroup.TRANSPORT, CategoryIcon.TRANSPORT),
    PARKING(R.string.fixed_parking, FixedExpenseGroup.TRANSPORT, CategoryIcon.PLACES),

    STREAMING(R.string.fixed_streaming, FixedExpenseGroup.SUBSCRIPTIONS, CategoryIcon.FILM),
    GYM(R.string.fixed_gym, FixedExpenseGroup.SUBSCRIPTIONS, CategoryIcon.SPORT),
    APPLICATIONS(R.string.fixed_applications, FixedExpenseGroup.SUBSCRIPTIONS, CategoryIcon.COMPUTER),

    CLEANING(R.string.fixed_cleaning, FixedExpenseGroup.OTHER, CategoryIcon.SPARKLES),
    PET_CARE(R.string.fixed_pet_care, FixedExpenseGroup.OTHER, CategoryIcon.ANIMALS),
    DONATIONS(R.string.fixed_donations, FixedExpenseGroup.OTHER, CategoryIcon.GIFTS),
    CUSTOM(R.string.fixed_custom, FixedExpenseGroup.OTHER, CategoryIcon.WALLET, custom = true),
}
