# 🛵 Autonomous Delivery Robot – Android Control & Management App

A complete Android system for controlling, monitoring, and managing an autonomous delivery robot using MQTT, sensors, Firebase, and live camera streaming.

---

## 🚀 Overview

This Android application is the main control interface for an IoT-based autonomous delivery robot.  
It provides robot navigation, delivery task automation, sensor monitoring, path/ location management, and full administrative features.

Built using Java (Android), MQTT, and Firebase, this app communicates in real time with a Raspberry Pi–powered robot that streams camera video and responds to movement commands, delivery instructions, and safety triggers (e.g., obstacle detection).

---

## ✨ Key Features

### 📡 MQTT Robot Control
- Send directional commands (forward, backward, left, right)
- Support for tilt-based driving via sensors
- MQTT-based communication with latency under ~100ms
- Auto-pause when robot detects an obstacle

### 📹 Live Camera Streaming
- Embedded WebView stream from Raspberry Pi camera
- Real-time feedback during autonomous or manual navigation

### 🧭 Delivery Management
- Create delivery orders
- Choose destination
- Track delivery status
- View completed delivery logs

### 📍 Location & Path Management
- Add, edit, and delete delivery locations
- Manage paths used for autonomous navigation
- Visual path representation through Firebase

### 🕵️ Admin Controls
- User management
- View logs & audit actions
- Separate admin dashboard

### 🔥 Firebase Integration
- Users
- Orders
- Paths
- Locations
- Action logs
- Real-time data synchronization

### 🧩 Modular UI

Activities & fragments include:

- MainActivity — Navigation hub
- ControlActivity — Manual & tilt control
- DeliveryActivity — Workflow for sending items
- SensorActivity — Sensor output debugging
- HistoryActivity — Completed deliveries
- LocationManagementActivity — CRUD over locations
- PathManagementActivity — Path editing
- AdminActivity — Admin dashboard

Multiple fragments (Home, Delivery, History, LocationMgmt, PathMgmt)

---

## 🧠 Architecture Overview

Robot → MQTT → App → Firebase → UI

- Robot publishes camera stream & sensor data
- App subscribes to MQTT topics
- User sends commands (movement, delivery start/pause)
- Firebase stores:
  - locations
  - paths
  - logs
  - delivery history
- UI updates live based on Firebase + MQTT events

If you'd like, I can add a diagram image here and generate it for you.

---

## 🛠️ Tech Stack

- Language: Java (Android)
- Architecture: Activity/Fragment-based modular structure
- Communication: MQTT (Eclipse Paho)
- Cloud: Firebase (Realtime Database, Firestore, or both depending on config)
- Robot hardware: Raspberry Pi + Sensors + Camera
- UI: XML layouts, animations, custom drawables
- Patterns: MVP-like separation via Service classes

---

## 🎥 Demo Video – Autonomous Delivery Robot in Action

Experience the robot navigating autonomously on predetermined paths, streaming live video, responding to real-time commands, avoiding obstacle and giving delevery updates

👉 **Watch the demo here:**
 

https://youtu.be/uJK1tU4ouAk



