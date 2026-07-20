# ♟️ VieChess - Backend API & Real-time Services

## 🌐 Deploy & Live Demo

- **Trang web ứng dụng:** [https://viechess.vercel.app/](https://viechess.vercel.app/)

### Các Dịch Vụ Sử Dụng Để Deploy:

| Thành phần | Dịch vụ Cloud / Platform | Mô tả |
| :--- | :--- | :--- |
| **Frontend** | [Vercel](https://vercel.com/) | Host ứng dụng Single Page Application (React + Vite) |
| **Backend** | [Railway](https://railway.app/) | Host dịch vụ API & WebSocket server (Spring Boot) |
| **PostgreSQL Database** | [NeonDB](https://neon.tech/) | Cơ sở dữ liệu quan hệ Serverless PostgreSQL |
| **Redis Cache** | [Upstash](https://upstash.com/) | Serverless Redis quản lý Caching & Session |
| **Message Broker (RabbitMQ)** | [CloudAMQP](https://www.cloudamqp.com/) | Quản lý Hàng đợi tin nhắn & Xử lý sự kiện Real-time |

---

## 📌 Giới Thiệu Dự Án (Backend)

Hệ thống Backend cho **VieChess** được phát triển bằng **Java Spring Boot**, cung cấp RESTful APIs, xác thực bảo mật JWT, dịch vụ truyền thông tin nhắn thời gian thực qua giao thức **WebSocket (STOMP)**, kết hợp cùng **RabbitMQ** làm Message Broker cho việc trao đổi dữ liệu asynchronous và **Redis** cho nhiệm vụ Caching.

---

## 🛠️ Công Nghệ Backend

- **Core Framework:** Java 17/21, Spring Boot 3
- **Security:** Spring Security, JWT (JSON Web Token)
- **Real-time Protocol:** Spring WebSocket, STOMP Messaging
- **Database & ORM:** PostgreSQL ([NeonDB](https://neon.tech/)), Spring Data JPA / Hibernate
- **Caching:** Redis ([Upstash](https://upstash.com/))
- **Message Queue:** RabbitMQ ([CloudAMQP](https://www.cloudamqp.com/))
- **Deployment Platform:** [Railway](https://railway.app/)