# E-Commerce REST API

REST API developed with Java and Spring Boot for an e-commerce platform.

The project is designed to support the sale of both physical and digital products, including product management, user accounts, shopping carts and orders.

## Features

- User registration and authentication
- JWT-based authentication
- Role-based authorization
- ADMIN, SUPPORT and CLIENT roles
- Product management
- Support for physical and digital products
- Shopping cart management
- Order management
- Email notifications
- Product filtering
- Pagination
- MySQL database
- RESTful endpoints

## Technologies

- Java
- Spring Boot
- Spring Security
- JWT
- Hibernate / JPA
- MySQL
- Maven
- ModelMapper
- JavaMail / SMTP

## Project Status

🚧 **Under construction**

The API is currently under development. Some features are implemented and functional, while others are still being tested and improved.

## Authentication

The API uses JWT tokens for authentication and role-based authorization.

Available roles:

- `ADMIN`
- `SUPPORT`
- `CLIENT`

## Architecture

DAO interfaces live in `com.api.crud.repositories`; their implementations live in
`com.api.crud.repositories.impl` and use the `DaoImp` suffix. Services depend on
the interfaces, while `@Repository` and persistence operations stay in the
implementation classes.

Java source uses four-space indentation, UTF-8, grouped imports and one blank
line between members. Fields and injected dependencies precede constructors and
methods; local variables stay near their use. Lombok getters/setters are declared
on individual fields, with one annotation per line. `.editorconfig` records the
basic whitespace conventions for editors.

The project follows a layered architecture separating:

- Controllers
- Services
- DAOs / Repositories
- Entities / Models
- DTOs
- Configuration
- Security

More documentation and examples will be added as the project progresses.

## Shopping cart behavior

Each user has one reusable cart. Automatic creation locks the user row within
the add-item transaction; `carts.user_id` must also have a unique constraint.
Before updating an existing database, check for duplicate carts:

```sql
SELECT user_id, COUNT(*) FROM carts GROUP BY user_id HAVING COUNT(*) > 1;
```

Existing duplicates must be reviewed and consolidated before applying the
unique constraint. No existing carts are automatically deleted or merged.

`GET /cart` returns an empty item list and total zero when no active cart exists,
without creating a database record. Cart subtotals use the current product price;
order items retain the price captured at purchase. Manual cart creation is disabled.

Cart item subtotals are calculated, not persisted. The legacy `priceCartItem`
column is no longer mapped, and `CartItemDTO` exposes `subtotal` instead.
Existing databases may retain the unused column with `ddl-auto=update`; its
removal must be handled separately when updating the database schema.

## Administrative cart changes

ADMIN and SUPPORT can use the following routes under `/admin/cart-items`:

| Method | Path | Request fields |
| --- | --- | --- |
| POST | (base path) | userId, productId, amount, reason |
| PUT | /{itemId}/amount | amount, reason |
| PATCH | /{itemId}/increase | amount, reason |
| PATCH | /{itemId}/decrease | amount, reason |
| DELETE | /{itemId} | reason |
| DELETE | /cart/{cartId}/clear | reason |

All requests use JSON bodies. Successful changes return 204. Quantities must be
positive; decreasing to zero or below removes the item. Adding, setting or
increasing quantities validates product availability and stock, without reserving
stock. Removing or decreasing remains possible for discontinued products.

Example quantity update:

```json
{"amount": 3, "reason": "Customer assistance"}
```

Changes and their audit records commit together. `cart_change_audit` stores the
authenticated actor ID, target user ID, cart/product IDs, action, old/new quantity,
reason and timestamp. Clearing a cart records each removed item; clearing an empty
cart records a zero-quantity event. Adding a product charges its current price;
these operations do not grant free products or discounts.

The schema now includes a version column on cart items, a unique cart/product pair
and the audit table. Concurrent item updates return 409 rather than silently
overwriting a previous change. Schema updates and concurrent behavior must also
be verified against MySQL before deployment.

Administrative cart lists return `CartAdminListDTO` (cart/user IDs, active state,
creation date, distinct item count and current total). Single-cart queries return
`CartAdminDetailDTO`, which adds item summaries.

`GET /admin/cart/getCarts` returns a page of summaries (an empty result has
`content: []`). Optional filters are minTotal, maxTotal (inclusive, nonnegative),
active, productId and minProductQuantity (positive; requires productId). With
productId alone, the cart must contain at least one unit of that product. Product
filters select carts but do not limit which lines contribute to their total.
Totals use current catalog prices and empty carts have total zero. Pagination
uses page (zero-based), size (default 10, maximum 100) and sort. Allowed sort
fields: idCart, userId, active, creationDate, itemCount, total; default idCart DESC.
Filtering, aggregation and pagination are performed by the database.

Examples:

```text
/admin/cart/getCarts?minTotal=50000&maxTotal=100000&active=true&page=0&size=10&sort=total,desc
/admin/cart/getCarts?productId=8&minProductQuantity=10
```

The former getCartsWithMoreThanNItems endpoint has been removed. Other dedicated
date-range and single-cart lookups retain their routes. No abandonment filter is
provided until last activity is tracked.

`PATCH /admin/cart/{id}/disable` preserves items and blocks further additions;
`PATCH /admin/cart/{id}/enable` explicitly re-enables the cart. Automatic creation
does not re-enable disabled carts. Both accept a JSON `reason` and return 204.
The exceptional `DELETE /admin/cart/deleteCartById/{id}` also requires a JSON
`reason`. State changes and deletions are audited in the same transaction; audit
records survive cart deletion. Carts now also have an optimistic version column.

## Request validation

All controller DTO request bodies use Bean Validation. Input errors return 400
ProblemDetail with an errors map where applicable; rejected values, passwords and
tokens are not included. Malformed JSON, invalid enum values and parameter types
also return 400. Business conflicts retain their existing 409 responses.

- Registration requires nonblank first/last names (2-25 characters), an email
  (maximum 254) and a new password. Phone is optional; when supplied it must use
  digits and common telephone separators (+, parentheses, spaces, dots, hyphens),
  contain a digit and be 8-20 characters long.
- New passwords require at least 8 Unicode code points, cannot be blank and must
  fit within 72 UTF-8 bytes (BCrypt limit). Confirmation must match. Existing
  passwords submitted to login/currentPassword can be shorter, so legacy accounts
  can still authenticate and change their password. Passwords are not trimmed.
- Profile/product patches require at least one field. Optional omitted fields
  remain unchanged; supplied fields must satisfy their constraints.
- Physical product creation requires stock and forbids digital-only fields;
  digital products require an HTTP(S) download URL and forbid stock. Image URLs
  must be HTTP(S) when supplied; an empty image URL is allowed for clearing it.
  Product IDs on creation must be omitted/null because the server generates them.
- Quantities and route IDs must be positive; stored cart/order item quantities
  cannot exceed 99999999. Reasons retain their nonblank, maximum-500 policy.
- Role names use 2-20 uppercase letters, digits or underscores, starting with a
  letter. Search inputs have corresponding field-length limits.
- Price ranges must be nonnegative and ordered. Catalog/user page sizes are
  capped at 100. Invalid user sort syntax/properties are rejected. Existing order
  and cart pagination/filter checks remain in effect.
- Percentage price adjustments round to two decimals with HALF_UP and must leave
  a positive price no greater than 99999999.99. Stock adjustments reject negative
  results and integer overflow rather than silently wrapping or returning 404.

These are API validation changes and do not execute database migrations.

## JWT signing and sessions

JWTUtil reads the required JWT_ECO environment variable; no signing secret or
fallback is stored in source. The value is used as UTF-8 key material (at least
32 bytes) with HS256. Supply an independently generated random secret for
deployment; moving the previous public source-code value into the environment
does not rotate it. Do not commit real secrets. Restart the application after
changing its environment. Tokens expire after 30 minutes.

Each user has authVersion (initially zero), separate from JPA's optimistic-lock
version. Login includes authVersion in the signed JWT. The filter accepts only
ACTIVE users whose stored version matches the claim. Changing a password,
administrative reset, email-token reset and account-status transitions increment
authVersion in the same transaction as the change. Existing sessions are then
rejected; log in again to get a new token. Legacy tokens lacking authVersion are
also rejected. Replacing JWT_ECO invalidates tokens signed with the previous key.

For an existing database, review/apply
src/main/resources/db/manual/2026-11-08-auth-version.sql if users.authVersion does
not exist. It initializes existing users to zero. This change does not execute
database migrations automatically.

## Account lifecycle

UserModel now stores one AccountStatus: ACTIVE, DEACTIVATED (voluntary departure)
or BLOCKED (administrative restriction). There is no independent active field.
Login and JWT authentication accept only ACTIVE accounts. Password recovery
changes the password but never changes account status.

Account recovery issues tokens only for DEACTIVATED users and rechecks this when
consuming a token. Blocking invalidates outstanding account-recovery tokens.
User state changes and token consumption use database locks and one transaction;
the state-change audit is persisted in the same transaction. Reused, expired or
pre-block tokens cannot bypass a block.

POST /users/account-recovery retains its generic response. PATCH /users/deactivate
records a voluntary departure without clearing carts or changing orders.
Administrative routes (ADMIN only) require a JSON reason, e.g.:

```json
{"reason": "Revision de seguridad"}
```

| Method | Route | Effect |
| --- | --- | --- |
| PATCH | /admin/users/{id}/block | ACTIVE or DEACTIVATED -> BLOCKED |
| PATCH | /admin/users/{id}/unblock | BLOCKED -> ACTIVE |
| PATCH | /admin/users/{id}/activate | DEACTIVATED -> ACTIVE; rejects BLOCKED |

The old administrative /deactivate endpoint is removed; use /block for restrictions.
Administrative status operations return 204. An administrator cannot block their
own account. Admin list/detail DTOs return status instead of active; user sorting
uses status instead of active.

GET /admin/users/{id}/status-history in AdminUserController is available to ADMIN/SUPPORT, with page/size
(default 10, maximum 100), ordered newest first. AccountStatusAuditResponseDTO
contains actor, target user, old/new status, source, reason and timestamp. Account
recovery records EMAIL_RECOVERY without storing a token in the audit.

Before starting this version against an existing database, run/review
src/main/resources/db/manual/2026-11-08-account-status.sql. It maps legacy active
users to ACTIVE and inactive users to BLOCKED because their original reason is
unknown. Review inactive accounts individually; ADMIN can unblock as appropriate.
It adds users.status and users.version. Do not rely on ddl-auto=update to migrate
the boolean into enum values. The legacy active column can be removed separately
after verification. No database migration is executed by this change automatically.

## Orders workflow

Online payment integration and setup are documented in [PAYMENTS.md](PAYMENTS.md).

Physical delivery uses one shipment per order. Set `ORDER_SHIPPING_FLAT_RATE` to
the nonnegative fixed delivery amount (up to two decimal places, in the same
monetary unit as product prices). There is no implicit free-shipping default:
physical checkout and `GET /orders/shipping-rate` return 503 until the rate is
configured. Set it explicitly to `0` for free shipping. Negative, malformed or
out-of-range configured rates prevent startup.

`POST /orders/createOrder` accepts this body for physical or mixed carts:

```json
{
  "shippingAddress": {
    "recipientName": "Nombre del destinatario",
    "phone": "+54 11 1234-5678",
    "street": "Calle de entrega",
    "streetNumber": "123",
    "apartment": "Piso 2",
    "city": "Ciudad",
    "region": "Provincia",
    "postalCode": "1234",
    "countryCode": "AR"
  }
}
```

Apartment is optional. Country must be a valid uppercase two-letter ISO code.
The address is an order-owned snapshot with non-updatable persistence columns;
profile changes never modify it. The server snapshots the current fixed shipping
rate once per physical/mixed order, regardless of item count. Order details return
`itemsSubtotal`, `shippingCost`, `totalOrder`, `shippingAddress` and
`shipmentTracking`; list DTOs omit the address and tracking payload. The total
includes shipping and keeps the existing monetary limit. Client-supplied prices
or shipping costs are not checkout inputs. Digital-only checkout still accepts
an empty body or `{}`, requires no delivery address, and has zero shipping cost.

ADMIN/SUPPORT can call `PATCH /admin/orders/{orderId}/shipment` with:

```json
{
  "carrier": "Transportista",
  "trackingNumber": "TRACK123",
  "trackingUrl": "https://example.com/track/TRACK123",
  "reason": "Registro del despacho"
}
```

Tracking URL is optional; null or an empty value clears it. Tracking updates
require a physical/mixed PAID order in CONFIRMED or SHIPPED state and record
each changed field with actor, previous/new values and reason. They never change
the order address or price. Marking a physical order SHIPPED requires a complete
address, carrier and tracking number; SHIPPED/DELIVERED transitions record their
respective timestamps. Corrections while SHIPPED preserve the dispatch timestamp;
DELIVERED tracking is read-only. Tracking is manual, without a carrier integration.

Apply `src/main/resources/db/manual/2026-11-08-order-shipping.sql` to an existing
database before startup, checking for columns Hibernate may already have added.
It preserves previous totals as product subtotals and defaults historical shipping
to zero. Review actual addresses/tracking for legacy physical orders still to be
shipped; historical delivery data cannot be inferred from current profiles.

Digital products allow one unit per product in a cart/order. Checkout snapshots
the digital source in `OrderItem.digitalDownloadLink`; later catalog edits or
deactivation do not replace the purchased source. Licenses are not delivered by
this flow. Digital rights are created transactionally when payment becomes PAID,
one record per order item. REFUNDED revokes rights and outstanding tokens under
the same order lock. Existing administrative refund eligibility is unchanged.

Digital delivery routes:

- `GET /orders/{orderId}/digital-deliveries`: buyer-owned delivery history.
- `POST /digital-deliveries/{id}/download-token`: creates a fifteen-minute permission.
- `GET /digital-deliveries/{id}/download`: authenticated buyer sends the permission
  in `X-Download-Token`, in addition to the JWT Authorization header.
- `GET /admin/digital-deliveries/order/{orderId}`: ADMIN/SUPPORT inspection.
- `POST /admin/digital-deliveries/{id}/reopen`: ADMIN/SUPPORT, JSON `reason`.

The permission is stored only as SHA-256. Issuing another permission replaces the
old one; expiration without use does not consume the download right. Claiming a
valid permission atomically marks the delivery USED before transfer begins. All
claims, reopenings and payment/refund operations lock the order before the
delivery, so concurrent claims cannot both succeed. An interrupted transfer or
upstream failure after claim still consumes that attempt. Support may reopen a
USED, still-paid delivery with a reason; refunded rights cannot be reopened.
Attempt count/time and reopening actions are retained in the audit. A refund
cannot erase a file already received or stop an already claimed transfer.

Files are proxied through the backend without exposing the source URL. Set
`DIGITAL_DOWNLOAD_ALLOWED_HOSTS` to a comma-separated list of exact, trusted HTTPS
hosts before enabling downloads (empty by default). Sources must be direct file
URLs, not HTML pages or redirects; no provider-specific login or signing adapter
is included. The upstream source must remain usable by the server. Use private
storage or unguessable protected origins; never place sources in public DTOs.
Only trusted hosts belong in the allowlist; arbitrary customer-controlled hosts
must not be allowed. Local/private destinations, credentials in URLs, redirects
and non-HTTPS sources are rejected. Token lifetime defaults to fifteen minutes via
`DIGITAL_DOWNLOAD_TOKEN_MINUTES`; file limit defaults to 100 MiB via
`DIGITAL_DOWNLOAD_MAX_BYTES`. Transfers have connect/read timeouts. HEAD and Range
requests cannot consume a permission; retries/resume require staff reopening.

Apply `src/main/resources/db/manual/2026-11-08-digital-deliveries.sql` before
starting against an existing database, first checking whether Hibernate has
already changed the schema. For legacy digital orders, review the actual
purchased source and any prior delivery before initializing rights; the script
documents the optional manual backfill. Current catalog URLs cannot reconstruct
historical purchased content automatically.

Unpaid orders reserve inventory for 30 minutes by default. Set
`ORDER_RESERVATION_MINUTES` to a positive number of minutes to change this period.
New orders store `expiresAt`, which is also returned by the order DTOs. Existing
deadlines do not change when the configured period changes.

The expiration scheduler checks every 60000 ms (configurable through
`ORDER_EXPIRATION_CHECK_DELAY_MS`). It processes candidates in pages of 100 IDs,
with a separate transaction and an order lock for each reservation. Orders still
in PENDING with payment PENDING or FAILED become EXPIRED when their deadline is
reached. Stock is restored once through `stockDeducted`; paid, canceled and
confirmed orders are excluded. Failures roll back that order and are retried in
the next cycle without preventing other candidates from being processed.

Payment changes on an overdue unpaid reservation return 409 even if the scheduler
has not processed it yet. The scheduler subsequently releases its inventory. A
successful payment made before expiry prevents automatic expiration. EXPIRED is
an automatic state; it cannot be assigned through the manual status endpoint.
Expired orders and their items remain available in history.

Automatic expiration writes an EXPIRE audit with a null `actorUserId` to identify
a system action. Before starting this version against an existing MySQL database,
apply `src/main/resources/db/manual/2026-11-08-order-expiration.sql`, checking the
current schema first if Hibernate has already updated it. The script adds the
deadline/index, extends the order status enum, allows null audit actors and
backfills pending unpaid orders. Set its reservation period to match the app.
Legacy pending orders without a stored deadline also use purchase date plus the
configured reservation period, so they do not reserve inventory indefinitely.

Users are deactivated rather than physically deleted. The former administrative
DeleteUserById endpoint and deletion methods have been removed. User-to-order
mapping does not cascade deletion; order history is retained. Cart clearing is
an independent operation and is not triggered by account deactivation. Automatic
cleanup of unused accounts is not implemented.

Client endpoints require authentication and use the JWT identity:

| Method | Path | Response |
| --- | --- | --- |
| POST | /orders | 201 with OrderDetailDTO and Location header |
| GET | /orders | Page of OrderListDTO |
| GET | /orders/{orderId} | OrderDetailDTO, own orders only |
| PATCH | /orders/{orderId}/cancel | 204; JSON reason required |

POST /orders takes no items or prices: it purchases the entire active cart.
An empty or disabled cart returns 409. Checkout locks the cart and products,
revalidates availability, deducts physical stock and captures the product name
and unit price in each order item. Saving the order, clearing the reusable cart
and writing the audit event belong to one transaction.

Client cancellation is allowed only for PENDING orders with PENDING or FAILED
payment. It restores stock once; repeating cancellation is a no-op. Client lists
support status/paymentStatus filters and page/size/sort parameters.

ADMIN and SUPPORT can query GET /admin/orders and GET /admin/orders/{orderId}.
The list returns OrderAdminListDTO and accepts email, status, paymentStatus,
start/end (ISO datetime), page, size (maximum 100) and sort filters. Detail returns
OrderAdminDetailDTO. Client responses omit buyer identity; list responses omit items.

Administrative PATCH routes:

- /admin/orders/{orderId}/status: JSON status and reason.
- /admin/orders/{orderId}/cancel: JSON reason.
- /admin/orders/{orderId}/payment-status: JSON status and reason; ADMIN only.

Order progression requires PAID: PENDING -> CONFIRMED -> SHIPPED -> DELIVERED.
Digital-only orders go from CONFIRMED directly to DELIVERED. Administrative
cancellation is limited to PENDING/CONFIRMED orders and requires any paid amount
to be marked REFUNDED first. Shipped/delivered returns require a separate future flow.
Manual payment transitions are PENDING -> PAID/FAILED, FAILED -> PENDING/PAID,
and PAID -> REFUNDED, only before shipping. This legacy endpoint applies only to
orders without an online checkout. Once a Mercado Pago checkout exists, manual
payment changes are rejected and only verified provider confirmations update it.

There is no exposed order-deletion endpoint. Order items are read-only:
GET /order-items/{itemId} and GET /order-items/order/{orderId} require ownership
and return OrderItemResponseDTO. ADMIN/SUPPORT can use the equivalent GET routes
under /admin/order-items; the individual lookup returns OrderItemAdminDetailDTO
with order ID, buyer ID and order/payment states. Lists reuse OrderItemResponseDTO.
An existing empty order returns 200 with []; missing or foreign resources return
404. All values use the purchased name and price. Legacy item mutation/subtotal
endpoints have been removed; item creation happens only during checkout.
Order changes are audited in order_change_audit within the same transaction.

Schema changes include orders.version, orders.stockDeducted (default false), products.version, order_items.productName
and the audit table. If existing orders are present, backfill productName before
enforcing NOT NULL; original historical names cannot be recovered from current
catalog data. New orders track stockDeducted so cancellation restores stock only
when checkout deducted it. Existing orders default to false because the previous
implementation did not deduct stock; review this value if stock was changed manually.
Verify schema, rollback and concurrent checkout against MySQL before deployment.

## Error responses

Exception classes and HTTP handlers live in `com.api.crud.exceptions`.
Services throw domain exceptions; repositories propagate persistence failures
instead of returning empty results or reporting a successful write. An absent
optional row is still a normal query result. Controllers delegate error responses
to the advice classes; JWT filter errors use the same JSON format.

Errors use `application/problem+json` with `type`, `title`, `status`, `detail`,
`instance` and a stable `code`. Validation errors also include an `errors`
object keyed by field or parameter. Internal exception messages, SQL and stack
traces are not returned to clients.

| Status | Meaning | Example code |
| --- | --- | --- |
| 400 | Invalid input or recovery token | INVALID_REQUEST |
| 401 | Missing/invalid session or credentials | UNAUTHENTICATED |
| 403 | Authenticated account lacks permission | FORBIDDEN |
| 404 | Requested resource does not exist or is not owned by the client | RESOURCE_NOT_FOUND |
| 409 | Business, integrity or concurrency conflict | BUSINESS_CONFLICT, INSUFFICIENT_STOCK, DATA_CONFLICT, CONCURRENT_MODIFICATION |
| 500 | Unexpected internal failure | INTERNAL_ERROR |
| 503 | Persistence or external service is unavailable | SERVICE_UNAVAILABLE, EXTERNAL_SERVICE_UNAVAILABLE |

Collection searches with no matches return 200 and an empty collection/page.
Password reset and account recovery requests retain their generic success message
when the email does not identify an eligible account.
