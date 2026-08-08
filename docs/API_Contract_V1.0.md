# StuDen REST API Contract — V1.0

**Base URL:** `/api/v1`  
**Format:** `application/json`  
**Authentication:** JWT Bearer token

```http
Authorization: Bearer <access_token>
```

## 1. Authentication

### Register
`POST /api/v1/auth/register`

Request:
```json
{
  "fullName": "Vivek Rao",
  "email": "vivek@example.com",
  "password": "SecurePassword123"
}
```

Response `201`:
```json
{
  "id": "uuid",
  "fullName": "Vivek Rao",
  "email": "vivek@example.com",
  "emailVerified": false,
  "accessToken": "jwt-token"
}
```

Validation: full name required, valid and unique email, password minimum 8 characters, password stored hashed.

### Login
`POST /api/v1/auth/login`

### Current User
`GET /api/v1/users/me`

### Update Current User
`PUT /api/v1/users/me`

### Public User Profile
`GET /api/v1/users/{userId}`

Returns public profile, portfolio, skills, education, certificates, showcase and published services.

## 2. Student Portfolio

A portfolio is optional and is created when a user chooses to become a freelancer.

- `POST /api/v1/portfolio` — create
- `GET /api/v1/portfolio/me` — get
- `PUT /api/v1/portfolio/me` — update
- `DELETE /api/v1/portfolio/me` — deactivate/delete

Example:
```json
{
  "headline": "Full Stack Developer",
  "about": "Computer science student passionate about building web applications.",
  "university": "Anurag University",
  "course": "B.Tech Computer Science",
  "graduationYear": 2027,
  "location": "Hyderabad",
  "availability": true
}
```

## 3. Skills

Skills are controlled by StuDen.

- `GET /api/v1/skills`
- `GET /api/v1/skills?categoryId={id}`
- `GET /api/v1/skills/search?q=react`
- `GET /api/v1/users/me/skills`
- `POST /api/v1/users/me/skills`
- `DELETE /api/v1/users/me/skills/{skillId}`

Add skill:
```json
{
  "skillId": "uuid"
}
```

## 4. Categories

`GET /api/v1/categories`

V1 categories:
- Development
- Design
- Video & Content
- Photography
- Music
- Dance
- Tutoring
- Fitness
- Event Services
- Creative & Professional Services

## 5. Education

- `GET /api/v1/users/me/education`
- `POST /api/v1/users/me/education`
- `PUT /api/v1/users/me/education/{educationId}`
- `DELETE /api/v1/users/me/education/{educationId}`

## 6. Certificates

- `GET /api/v1/users/me/certificates`
- `POST /api/v1/users/me/certificates`
- `PUT /api/v1/users/me/certificates/{certificateId}`
- `DELETE /api/v1/users/me/certificates/{certificateId}`

## 7. Showcase

Showcase supports images, videos, files and external links.

- `GET /api/v1/users/{userId}/showcase`
- `GET /api/v1/users/me/showcase`
- `POST /api/v1/users/me/showcase`
- `PUT /api/v1/users/me/showcase/{itemId}`
- `DELETE /api/v1/users/me/showcase/{itemId}`
- `PATCH /api/v1/users/me/showcase/reorder`

Example:
```json
{
  "title": "AI Recipe Generator",
  "description": "A web application that generates recipes using AI.",
  "type": "PROJECT",
  "mediaUrl": "...",
  "externalUrl": "https://github.com/..."
}
```

## 8. Services

- `GET /api/v1/services`
- `GET /api/v1/services/{serviceId}`
- `POST /api/v1/services`
- `PUT /api/v1/services/{serviceId}`
- `PATCH /api/v1/services/{serviceId}/pause`
- `PATCH /api/v1/services/{serviceId}/publish`
- `DELETE /api/v1/services/{serviceId}`

Search/filter example:
`GET /api/v1/services?search=web+development&categoryId=uuid&skillId=uuid&minPrice=500&maxPrice=5000&serviceMode=ONLINE&location=Hyderabad`

Create service:
```json
{
  "categoryId": "uuid",
  "title": "Spring Boot Backend Development",
  "description": "I will build REST APIs using Spring Boot.",
  "price": 2000,
  "pricingType": "FIXED",
  "serviceMode": "ONLINE",
  "location": "REMOTE",
  "estimatedDuration": "7 days",
  "requirements": "Please provide your project requirements.",
  "skillIds": ["java-id", "spring-boot-id", "sql-id"]
}
```

A user must have a Student Portfolio to create a service.

## 9. Service Images

- `POST /api/v1/services/{serviceId}/images` — multipart/form-data
- `DELETE /api/v1/services/{serviceId}/images/{imageId}`

## 10. Saved Services

- `POST /api/v1/services/{serviceId}/save`
- `DELETE /api/v1/services/{serviceId}/save`
- `GET /api/v1/users/me/saved-services`

## 11. Booking Requests

V1 uses booking requests, not payments or orders.

- `POST /api/v1/booking-requests`
- `GET /api/v1/booking-requests/sent`
- `GET /api/v1/booking-requests/received`
- `GET /api/v1/booking-requests/{requestId}`
- `PATCH /api/v1/booking-requests/{requestId}/accept`
- `PATCH /api/v1/booking-requests/{requestId}/decline`
- `PATCH /api/v1/booking-requests/{requestId}/cancel`
- `PATCH /api/v1/booking-requests/{requestId}/complete`

Example:
```json
{
  "serviceId": "uuid",
  "projectDescription": "I need a portfolio website for my startup.",
  "budget": 3000,
  "deadline": "2026-08-20"
}
```

V1 completion only changes the request status. No payment is triggered.

## 12. Booking Attachments

`POST /api/v1/booking-requests/{requestId}/attachments`

Supports images, PDFs and documents.

## 13. Conversations

- `GET /api/v1/conversations`
- `GET /api/v1/conversations/{conversationId}`
- `POST /api/v1/conversations`

Start conversation:
```json
{
  "userId": "uuid"
}
```

If a conversation already exists, return it instead of creating a duplicate.

## 14. Messages

- `GET /api/v1/conversations/{conversationId}/messages`
- `POST /api/v1/conversations/{conversationId}/messages`
- `POST /api/v1/conversations/{conversationId}/messages/attachment`
- `PATCH /api/v1/messages/{messageId}/read`

Send message:
```json
{
  "message": "Hi, I have a few questions about your service."
}
```

## 15. Notifications

- `GET /api/v1/notifications`
- `PATCH /api/v1/notifications/{notificationId}/read`
- `PATCH /api/v1/notifications/read-all`

## 16. Reports

`POST /api/v1/reports`

Example:
```json
{
  "reportedUserId": "uuid",
  "serviceId": "uuid",
  "reason": "INAPPROPRIATE_CONTENT",
  "description": "Description of the issue."
}
```

## 17. Admin APIs

Admin authentication required.

Users:
- `GET /api/v1/admin/users`
- `GET /api/v1/admin/users/{userId}`
- `PATCH /api/v1/admin/users/{userId}/suspend`
- `PATCH /api/v1/admin/users/{userId}/activate`

Services:
- `GET /api/v1/admin/services`
- `DELETE /api/v1/admin/services/{serviceId}`

Reports:
- `GET /api/v1/admin/reports`
- `PATCH /api/v1/admin/reports/{reportId}/resolve`

## 18. Standard Error Response

```json
{
  "timestamp": "2026-08-08T10:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Invalid request",
  "path": "/api/v1/services"
}
```

Standard status codes:

| Status | Meaning |
|---|---|
| 200 | Successful request |
| 201 | Resource created |
| 204 | Successful deletion/no content |
| 400 | Invalid request |
| 401 | Not authenticated |
| 403 | Not authorized |
| 404 | Resource not found |
| 409 | Conflict |
| 422 | Validation/business rule failure |
| 500 | Server error |

## 19. Authorization Rules

### Every User Can
- Edit their own profile
- Send booking requests
- Save services
- Send messages
- Report users/services

### Users With a Student Portfolio Can
- Manage their portfolio
- Manage their skills
- Upload showcase items
- Create services
- Edit their own services
- Accept/decline requests for their services

### Users Cannot
- Edit another user's profile
- Edit another user's portfolio
- Edit another user's service
- Accept another user's booking
- Delete another user's showcase
- Access admin APIs

### Admin Can
- Moderate users
- Moderate services
- Review reports
- Suspend/activate accounts

## 20. V1 Exclusions

Not part of V1.0:
- Payments
- Orders
- Payouts
- Wallet
- Reviews
- Ratings
- Subscriptions
- Ads
- Escrow
- AI services

## 21. API Design Rules

1. Use RESTful resource names.
2. Use plural nouns for collections.
3. Use HTTP status codes consistently.
4. Keep authentication and authorization on the backend.
5. Never trust frontend ownership claims.
6. Validate all request bodies server-side.
7. Return consistent error responses.
8. Use UUIDs for public entity identifiers.
9. Never expose password hashes.
10. Do not add V2 payment/review functionality to V1 endpoints.

## 22. User Model

StuDen does not use separate Buyer and Seller accounts.

A single User can both request and offer services.

```text
User ID: 123

As requester:
booking_request.buyer_user_id = 123

As service provider:
booking_request.seller_user_id = 123
```

No role switching or duplicate accounts are required.

## 23. Core User Flows

### Requesting a service
```text
Register
  ↓
Explore
  ↓
Search
  ↓
View Service
  ↓
View Student Showcase
  ↓
Send Booking Request
  ↓
Accept / Decline
  ↓
Messages
  ↓
Complete
```

### Offering a service
```text
Become a Freelancer
  ↓
Student Portfolio
  ↓
Select Categories
  ↓
Select Skills
  ↓
Upload Showcase
  ↓
Create Service
  ↓
Publish
```

## 24. Status

**FROZEN FOR STU​DEN V1.0**

This document is the agreed API contract between the frontend and backend. Intentional API changes should be documented as a new contract revision.
