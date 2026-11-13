# Attendance System

Attendance System is a team project for tracking student attendance. Faculty staff can import student lists, organize courses and teaching groups, create class sessions, and review attendance. Students can view their courses and check in while a session is active.

## Features

- Import student lists from Excel (`.xlsx`) and CSV files, with a preview before saving.
- Organize courses by academic year, study program, class type, and group.
- Assign first-year students to lecture exercise and laboratory groups automatically; configure groups and allow group selection for students in later years.
- Sign in with an institutional Google account, with a separate local login for a test employee.
- Create sessions and record attendance using a time-limited QR code or PIN.
- Update session information in real time through WebSocket.
- View attendance analytics by student, session, and group.
- Export session and course-wide attendance reports as Excel and PDF files.

## Technology and architecture

The frontend uses React and TypeScript. The Spring Boot backend is split into `course-service` for courses and attendance, `auth-service` for authentication, and `analytics-service` for reporting. Services communicate through REST APIs. Spring Cloud Config Server provides external configuration, and Eureka handles service discovery.

Course, student, and attendance records are stored in PostgreSQL. The analytics service has its own PostgreSQL database, which is updated from changes in the main database. Docker Compose runs the complete application.

## Running locally

Docker and Docker Compose are required. Run the following command from the project root:

```bash
docker compose up -d --build
```

The application is available at `http://localhost:5173`. Use `docker compose ps` to check container status and `docker compose down` to stop the application.

Google sign-in requires a `GOOGLE_CLIENT_ID` configured for your own OAuth client. Local settings can be placed in a `.env` file in the project root; this file should not be committed. The default values in the Compose configuration are intended for local development and must be replaced before exposing the application publicly.

## My contribution

I worked on importing student lists, linking students and staff to courses, and assigning students to lecture exercise and laboratory groups. I also contributed to user authentication, sign-in, and session management. The analytics view was initially developed as part of the team; I moved it into a separate service and integrated it with the rest of the application.

## Future improvements

The application can be extended with additional import validation, more analytics views, interface improvements, and broader automated test coverage. Production use would also require environment-specific configuration, credentials, and public access settings.
