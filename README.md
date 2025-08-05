# Team03-BrisaHackathon

## Smart Toll Detection System for Android Automotive OS

An intelligent toll detection application designed for Android Automotive OS that automatically detects when a vehicle crosses toll points using GPS data and geofencing technology.

## Project Overview

This application consists of two main modules that work together to provide seamless toll detection and tracking:

### **LocationProvider Module**
- **Purpose**: Captures and provides real-time GPS location data from the vehicle
- **Key Features**:
  - Background service with foreground notification
  - AIDL interface for inter-process communication
  - Automatic startup on device boot

### **LocationReceiver Module** 
- **Purpose**: Processes location data, detects toll crossings, and communicates with Brisa API
- **Key Features**:
  - Geofence-based toll detection algorithm
  - Real-time toll data fetching from Brisa API
  - Automatic trip logging and toll payment processing
  - Text-to-speech notifications for toll events
  - Secure authentication token management
  - Smart caching system for nearby tolls

## 🔧 How It Works

1. **GPS Data Collection**: The LocationProvider module continuously tracks the vehicle's GPS position using Android's location services
2. **Data Processing**: The LocationReceiver module receives location updates via AIDL interface
3. **Toll Detection**: The system checks if the current position falls within any toll geofences using point-in-polygon algorithms
4. **API Integration**: When a toll is detected, the system:
   - Authenticates with Brisa API using secure credentials
   - Fetches current toll information and geofences
   - Logs the toll crossing event
   - Sends trip data to Brisa for billing purposes
5. **User Feedback**: Provides audio notifications in Portuguese when entering/exiting toll areas

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Android Automotive OS                    │
├─────────────────────────────────────────────────────────────┤
│  LocationProvider Module          LocationReceiver Module   │
│  ┌─────────────────────┐         ┌─────────────────────────┐│
│  │ GPS Location Service│◄────────┤ Toll Detection Service ││|
│  │ - FusedLocationAPI  │  AIDL   │ - Geofencing           ││|
│  │ - Background Service│         │ - Point-in-Polygon     ││|
│  │ - Boot Receiver     │         │ - TTS Notifications    ││|
│  └─────────────────────┘         └─────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
                  ┌─────────────────┐
                  │   Brisa API     │
                  │ - Authentication│
                  │ - Toll Data     │
                  │ - Trip Logging  │
                  └─────────────────┘
```

## Installation & Setup

### Prerequisites
- Android Automotive OS device or emulator (API level 34+)
- Android Studio with Kotlin support
- Valid Brisa API credentials

### Build Configuration

1. Clone the repository:
```bash
git clone https://github.com/SEAME-pt/Team03-BrisaHackathon.git
cd Team03-BrisaHackathon
```

2. Configure API credentials by creating a `local.properties` file:
```properties
API_EMAIL=your_brisa_email@example.com
API_PASSWORD=your_brisa_password
API_PLATE=XX-XX-XX
```

3. Build the project:
```bash
./gradlew build
```

4. Install both modules on the Android Automotive device:
```bash
./gradlew :locationprovider:installDebug
./gradlew :locationreceiver:installDebug
```

### Permissions Setup

The application requires the following permissions (automatically requested):
- `ACCESS_FINE_LOCATION`: For precise GPS positioning
- `ACCESS_BACKGROUND_LOCATION`: For continuous tracking while driving
- `RECEIVE_BOOT_COMPLETED`: For automatic startup
- `INTERNET`: For Brisa API communication

## Usage

1. **Initial Setup**: Launch both applications to grant required permissions
2. **Automatic Operation**: The system automatically starts on device boot and runs in the background
3. **Toll Detection**: When approaching a toll:
   - Audio notification: "Nova viagem iniciada em [Toll Name]"
   - Trip logging begins automatically
4. **Exit Detection**: When leaving a toll area:
   - Audio notification: "Viagem concluída em [Toll Name]"
   - Trip data sent to Brisa API for billing

## Technical Features

### Geofencing Algorithm
- **Point-in-Polygon Detection**: Uses ray casting algorithm to determine if GPS coordinates fall within toll geofences
- **Smart Caching**: Maintains a 50km radius cache of nearby tolls, updated every 5 minutes
- **Distance Calculation**: Haversine formula for accurate distance calculation

## Security Features

- **Secure Storage**: Encrypted storage for API credentials and tokens
- **Token Management**: Automatic token refresh and secure cleanup
- **Network Security**: HTTPS-only communication with Brisa API

## Development

### Key Technologies
- **Kotlin**: Primary programming language
- **Android Automotive OS**: Target platform
- **AIDL**: Inter-process communication
- **Ktor**: HTTP client for API communication
- **Kotlin Serialization**: JSON parsing
- **FusedLocationProvider**: GPS services
- **TextToSpeech**: Audio notifications

### Module Structure
```
├── locationprovider/           # GPS data collection module
│   ├── src/main/java/
│   │   └── com/example/locationprovider/
│   │       ├── LocationProviderService.kt
│   │       ├── MainActivity.kt
│   │       └── BootCompletedProvider.kt
│   └── src/main/aidl/          # AIDL interface definitions
├── locationreceiver/           # Toll detection and API module  
│   ├── src/main/java/
│   │   └── com/example/locationreceiver/
│   │       ├── LocationReceiverService.kt
│   │       ├── MainActivity.kt
│   │       ├── BootCompletedReceiver.kt
│   │       └── util/
│   │           ├── TollModels.kt
│   │           └── SecureStorage.kt
│   └── src/main/aidl/          # AIDL interface definitions
```