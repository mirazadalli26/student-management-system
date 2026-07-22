# Student Management System (SMS)

A full-stack Student Management System with a Spring Boot + MySQL backend and a
Tailwind CSS frontend. An administrator manages student records (create, view,
edit, delete); each student logs in to view only their own details, fees, and
outstanding balance. Authentication is JWT-based with role-based access control
(`ADMIN`, `STUDENT`).

## Tech Stack

| Layer    | Technology                                              |
| -------- | ------------------------------------------------------- |
| Frontend | HTML5, CSS3, vanilla JavaScript, Tailwind CSS (via CDN) |
| Backend  | Spring Boot 3.2.5 (Web, Security, Data JPA, Validation) |
| Auth     | JWT (JJWT), BCrypt password hashing                     |
| Database | MySQL (default) or in-memory H2 (dev profile)           |
| Build    | Maven (wrapper included)                                |
| Java     | 17                                                      |

## Prerequisites

- **JDK 17** (`java -version` should report 17.x)
- **Maven** — not required; the project ships a wrapper (`./mvnw`) that downloads
  Maven automatically on first use
- **MySQL 8.x** — only if you run the default (MySQL) profile

---

## Quick Start (no database setup)

The fastest way to try the app. Uses an in-memory **H2** database, so no MySQL
install is needed. Data resets every time the app restarts.

```bash
cd sms
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Then open <http://localhost:8080/login.html> and sign in with the default admin
(see [Default Login](#default-login)).

> On Windows use `mvnw.cmd` instead of `./mvnw`.

---

## Running with MySQL (default profile)

Use this for persistent data.

### 1. Install and start MySQL

**macOS (Homebrew):**

```bash
brew install mysql
brew services start mysql
```

**Ubuntu/Debian:**

```bash
sudo apt update && sudo apt install mysql-server
sudo systemctl start mysql
```

**Windows:** install MySQL Community Server from the official installer and start
the MySQL service.

### 2. Align the database credentials

The app reads its datasource config from
`src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/sms?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=root
```

The `sms` database is created automatically (`createDatabaseIfNotExist=true`),
so you only need the MySQL server running with matching credentials. Two options:

- **Match the config to MySQL:** set your MySQL `root` password to `root`, or
- **Match the config to your MySQL:** edit `username`/`password` above to your
  actual credentials.

> A fresh Homebrew MySQL creates `root` with an **empty** password. Either set a
> password (`mysql_secure_installation`) or set `spring.datasource.password=` (blank).

### 3. Run

```bash
cd sms
./mvnw spring-boot:run
```

Open <http://localhost:8080/login.html>.

---

## Default Login

The default administrator is provisioned automatically at startup (configured in
`application.properties`):

| Field    | Value                     |
| -------- | ------------------------- |
| Username | `admin`                   |
| Password | `changeit-admin-password` |

Change these before any real use:

```properties
sms.admin.username=admin
sms.admin.password=your-strong-password
```

Restart the app after changing them. (With MySQL, the admin is only created if no
admin already exists; delete the existing admin row or use a fresh database to
re-provision with new credentials.)

Students do not self-register — the admin creates each student in the **Add
Student** form, and the student then logs in with the username/password set there.

---

## How to Use

1. **Log in as admin** at `/login.html`.
2. **View Students** — see all students in a table with Edit/Delete per row.
3. **Add Student** — fill the form (name, course, mobile, email, total fees, paid
   fees, username, password) and Submit.
4. **Edit** — opens the same form pre-filled; change fields and Update. Leave the
   password blank to keep the existing one.
5. **Delete** — asks for confirmation before removing the record.
6. **Student login** — a student signs in and sees only their own details, fees,
   and outstanding balance.

---

## Build

Produce a runnable jar:

```bash
cd sms
./mvnw clean package
```

The executable jar is written to `target/student-management-system.jar`.

Run the jar:

```bash
# MySQL (default)
java -jar target/student-management-system.jar

# H2 in-memory dev mode
java -jar target/student-management-system.jar --spring.profiles.active=dev
```

---

## Configuration Reference

All settings live in `src/main/resources/application.properties`
(base / MySQL profile) and `application-dev.properties` (H2 dev profile).

| Property                     | Purpose                                         |
| ---------------------------- | ----------------------------------------------- |
| `server.port`                | HTTP port (default `8080`)                      |
| `spring.datasource.url`      | JDBC URL for MySQL                              |
| `spring.datasource.username` | Database user                                   |
| `spring.datasource.password` | Database password                               |
| `sms.admin.username`         | Default admin username                          |
| `sms.admin.password`         | Default admin password                          |
| `sms.jwt.secret`             | Base64-encoded signing key (256-bit min)        |
| `sms.jwt.expiration-seconds` | Token lifetime in seconds (default `900` = 15m) |

### Environment variable overrides (recommended for real deployments)

Spring maps env vars to properties, so you can avoid committing secrets:

```bash
export SMS_ADMIN_USERNAME=admin
export SMS_ADMIN_PASSWORD='a-strong-password'
export SPRING_DATASOURCE_PASSWORD='db-password'
export SMS_JWT_SECRET='<base64-256-bit-secret>'
./mvnw spring-boot:run
```

---

## Project Structure

```
sms/
├── pom.xml
├── mvnw / mvnw.cmd                     # Maven wrapper
└── src/main/
    ├── java/com/sms/
    │   ├── config/                     # DataInitializer (default admin), SecurityConfig
    │   ├── controller/                 # AuthController, StudentController
    │   ├── dto/                        # request/response DTOs + validation
    │   ├── entity/                     # Admin, Student, Role
    │   ├── exception/                  # custom exceptions + GlobalExceptionHandler
    │   ├── repository/                 # Spring Data JPA repositories
    │   ├── security/                   # JwtUtils, JwtAuthFilter, UserDetailsService, LoginAttemptService
    │   ├── service/                    # StudentService (business logic + validation)
    │   └── StudentManagementApplication.java
    └── resources/
        ├── application.properties      # base / MySQL profile
        ├── application-dev.properties  # H2 in-memory profile
        └── static/                     # login.html, dashboard.html, app.js (Tailwind frontend)
```

---

## Security Notes

- The default admin password and `sms.jwt.secret` shipped in
  `application.properties` are **development defaults** — rotate both before
  deploying anywhere real (prefer environment variables).
- Passwords are stored as BCrypt hashes; JWTs expire after 15 minutes.
- The login lockout (5 failed attempts / 15 minutes) is tracked in memory, which
  fits a single-instance deployment. Running multiple instances would need a
  shared store (e.g. Redis) for consistent lockout.

---

## Troubleshooting

**`Communications link failure` / `Connection refused` on startup**
MySQL isn't running or the credentials don't match. Start MySQL and verify the
`spring.datasource.*` values, or use the H2 dev profile
(`-Dspring-boot.run.profiles=dev`) to run without MySQL.

**`Access denied for user 'root'@'localhost'`**
The MySQL password doesn't match `spring.datasource.password`. Update one to match
the other.

**Port 8080 already in use**
Change `server.port` in `application.properties`, or stop the process using 8080.

**`./mvnw: Permission denied`**
Run `chmod +x mvnw` once.

**Login always fails after changing the admin password (MySQL)**
The admin is only seeded when none exists. Remove the old admin row (or use a
fresh database) so it gets re-provisioned with the new credentials:

```sql
-- from a mysql client, if needed:
USE sms;
DELETE FROM admin WHERE username = 'admin';
```
