# 🛒 E-Commerce POC (Angular + Spring Boot)

A full-stack Proof of Concept (POC) for a scalable e-commerce platform built using **Angular**, **Spring Boot**, **MySQL**, **Kafka**, and **ElasticSearch**.

## 📦 Features

- **Dynamic Product Catalog & Wishlist** — Angular front-end  
- **Shopping Cart & Checkout Flow** — Angular  
- **Scalable Backend API** — Spring Boot + MySQL  
- **Asynchronous Processing** — Kafka (e.g., order fulfillment)  
- **High-Speed Search** — ElasticSearch integration  
- **End-to-End Runnable Environment** for development & scaling  

---

# 🚀 Project Setup Guide

Follow the steps below to run the application locally.

---

## 🔧 Prerequisites

Ensure the following tools are installed:

- **Node.js & npm** — LTS 18+  
- **Angular CLI**  
  ```bash
  npm install -g @angular/cli
- **Java Development Kit (JDK)**: (Version 17+)
- **Maven:** (For building the backend server)
- **Infrastructure Services:** Running instances of **MySQL, Kafka,** and **ElasticSearch**.
## Infrastructure Initialization
  ```bash
  # Connect to MySQL and run the schema script
  mysql -u [your_user] -p ecommerce_db < db-scripts.sql



