// Short names for the generated OpenAPI types. Never hand-write DTOs: regenerate with `npm run api`.
import type { components } from "./schema";

type S = components["schemas"];

export type ProductCard = S["ProductCardResponse"];
/** One page of the catalog search. */
export type SearchPage = S["PageResponseProductCardResponse"];
export type ProductDetail = S["ProductDetailResponse"];
export type Sku = S["SkuResponse"];
export type TranslatedPair = S["TranslatedPair"];
export type PriceBreakdown = S["PriceBreakdown"];
export type Category = S["CategoryResponse"];
export type PricingSettings = S["PublicPricingSettingsResponse"];

export type Cart = S["CartResponse"];
export type CartShop = S["CartShopResponse"];
export type CartItem = S["CartItemResponse"];
export type CartTotals = S["CartTotals"];

export type CreateOrderRequest = S["CreateOrderRequest"];
export type DeliveryMethod = CreateOrderRequest["deliveryMethod"];
export type PaymentProvider = S["StartPaymentRequest"]["provider"];
export type OrderDetail = S["OrderDetailResponse"];
export type OrderSummary = S["OrderSummaryResponse"];
export type OrderItem = S["OrderItemResponse"];
export type OrderStatus = NonNullable<OrderDetail["status"]>;
export type TimelineStep = S["Step"];
export type StartPaymentResponse = S["StartPaymentResponse"];

export type User = S["UserResponse"];
export type AuthResponse = S["AuthResponse"];
export type Address = S["AddressResponse"];
export type AddressRequest = S["AddressRequest"];
export type Wallet = S["WalletResponse"];
export type WalletTransaction = S["WalletTransactionResponse"];
export type WalletTransactionType = NonNullable<WalletTransaction["type"]>;
export type NotificationSettings = S["NotificationSettingsResponse"];
export type OtpChannel = S["OtpRequestResponse"]["channel"];
export type InAppNotification = S["InAppNotificationResponse"];

/** RFC 7807 body sent by the backend for every error. */
export type Problem = {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  changes?: unknown[];
};

export type Banner = S["PublicBannerResponse"];
export type TickerItem = S["PublicTickerMessageResponse"];
export type TickerMessage = S["TickerMessageResponse"];
export type ContentPage = S["PublicPageResponse"];

// Staff panel (/admin)
export type Role = NonNullable<User["roles"]>[number];
export type StaffOrder = S["StaffOrderResponse"];
export type StaffOrderSummary = S["StaffOrderSummaryResponse"];
export type OrderHistory = S["OrderHistoryResponse"];
export type OrderComment = S["OrderCommentResponse"];
export type QueueOrder = S["ProcurementQueueOrder"];
export type QueueItem = S["Item"];
export type Dashboard = S["DashboardResponse"];
export type DelayedOrder = S["DelayedOrderResponse"];
export type CustomerSupport = S["CustomerSupportResponse"];
export type Notification = S["NotificationResponse"];
export type NotificationStatus = NonNullable<Notification["status"]>;
export type StaffPricingSettings = S["PricingSettingsResponse"];
export type UpdatePricingSettings = S["UpdatePricingSettingsRequest"];
export type CbuRate = S["CbuRateResponse"];
export type MonthlyReport = S["MonthlyReportResponse"];
export type AdminBanner = S["BannerResponse"];
export type BannerRequest = S["BannerRequest"];
export type AdminCategory = S["AdminCategoryResponse"];
export type CategoryRequest = S["CategoryRequest"];
export type BlockedKeyword = S["BlockedKeywordResponse"];
export type BlockedKeywordRequest = S["BlockedKeywordRequest"];
export type PromoCode = S["PromoCodeResponse"];
export type PromoCodeRequest = S["PromoCodeRequest"];
export type AdminPage = S["ContentPageResponse"];
export type AdminPageRequest = S["ContentPageRequest"];
export type AuditEvent = S["AuditEventResponse"];
