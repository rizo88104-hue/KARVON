// Short names for the generated OpenAPI types. Never hand-write DTOs: regenerate with `npm run api`.
import type { components } from "./schema";

type S = components["schemas"];

export type ProductCard = S["ProductCardResponse"];
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

/** RFC 7807 body sent by the backend for every error. */
export type Problem = {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  changes?: unknown[];
};

export type Banner = S["PublicBannerResponse"];
export type ContentPage = S["PublicPageResponse"];
