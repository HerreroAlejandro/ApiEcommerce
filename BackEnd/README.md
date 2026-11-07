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
