package uz.karvon.payment.application.click;

/**
 * Builds Click signs in tests the way Click does (SHOP API formulas).
 */
public final class ClickTestSigns {

    private ClickTestSigns() {
    }

    public static String prepare(String clickTransId, String serviceId, String secret, String merchantTransId,
                                 String amount, String action, String signTime) {
        return ClickShopApi.md5(clickTransId + serviceId + secret + merchantTransId + amount + action + signTime);
    }

    public static String complete(String clickTransId, String serviceId, String secret, String merchantTransId,
                                  String prepareId, String amount, String action, String signTime) {
        return ClickShopApi.md5(clickTransId + serviceId + secret + merchantTransId + prepareId + amount + action + signTime);
    }
}
