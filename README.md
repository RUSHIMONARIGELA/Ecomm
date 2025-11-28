E-Commerce POC (Angular + Spring Boot)

This repository contains the Proof of Concept (POC) for a full-stack e-commerce application, featuring a dynamic product catalog, a shopping cart, and a user wishlist.

The backend leverages Spring Boot with MySQL as the primary data store, Kafka for asynchronous messaging (e.g., order processing), and ElasticSearch for high-speed product search and indexing. The frontend is built with Angular and styled using Bootstrap/Tailwind.

The goal of this repository is to provide a complete, runnable environment for further development and scaling.

🚀 Project Setup Guide

Follow these steps to get the project running locally.

Prerequisites

You must have the following installed on your system:

Node.js & npm: (LTS version recommended, 18+)

Angular CLI: Install globally via npm install -g @angular/cli

Java Development Kit (JDK): (Version 17+)

Maven: (For the backend server)

Database: MySQL (or compatible)

Messaging Queue: A locally running Kafka instance.

Search Engine: A locally running ElasticSearch instance.

1. Infrastructure Setup (Database, Kafka, ElasticSearch)

Before starting the backend, ensure all required infrastructure services are running:

Start Services: Manually start your local instances of MySQL, Kafka, and ElasticSearch.

Database Initialization: Connect to your running MySQL instance and execute the schema creation script:

# Example command for MySQL
mysql -u [your_user] -p ecommerce_db < db-scripts.sql


(Note: Ensure your database is named ecommerce_db and use the correct user/password combination.)

2. Backend Server Setup (Spring Boot)

The backend handles API requests, database/search/queue interactions, and business logic.

Navigate to the backend directory (assumed to be ecommerce-backend/):

cd ecommerce-backend


Configure Environment: Copy the example environment file and fill in your actual credentials, ensuring MySQL, Kafka, and ElasticSearch connection details are correct.

cp ../.env.example .env


CRITICAL: Edit the newly created .env file and update variables for DB_PASSWORD, KAFKA_BOOTSTRAP_SERVERS, and ES_HOST_URL.

Build and Run: Compile the project and start the server.

mvn clean install
mvn spring-boot:run


The server should start on http://localhost:8080.

3. Frontend Application Setup (Angular)

The frontend is a standalone Angular application.

Navigate back to the project root and then into the frontend directory (assumed to be ecommerce-frontend/):

cd ../ecommerce-frontend


Install Dependencies:

npm install


Configure Environment: Ensure the API_BASE_URL in your frontend configuration files (e.g., environment.ts) matches the backend host configured in .env.example.

Run the Application:

ng serve --open


The Angular application will typically start on http://localhost:4200.

⚙️ Environment and Configuration Files

File Name

Description

Used By

.env.example

Template containing all required environment variables for the database, Kafka, ElasticSearch, and application secrets (JWT_SECRET). Copy this to .env and fill in secrets.

Backend

db-scripts.sql

SQL script to create all core tables: customer, product, wishlist_item, and cart_item.

Database

ecommerce-backend/

Backend source code directory (Spring Boot).

Developer

ecommerce-frontend/

Frontend source code directory (Angular).

Developer
