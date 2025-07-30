#include <gtest/gtest.h>
#include <fstream>
#include <string>
#include <vector>
#include <cmath>
#include <chrono>
#include "seal/seal.h"

using namespace std;
using namespace seal;

// Forward declarations - we'll include the implementation after defining our structures
string trim(const string& str);
vector<struct GeofencePoint> parseGeofencePointsArray(const string& json);
struct Geofence parseSingleGeofence(const string& json);
vector<struct Geofence> parseGeofencesArray(const string& json);
struct TollZone parseSingleTollZone(const string& json);
vector<struct TollZone> loadTollZonesFromJSON(const string& filename);
void preEncryptCentroids(vector<struct TollZone>& tollZones, Encryptor& encryptor, CKKSEncoder& encoder, double scale);
struct SpatialIndex buildSpatialIndex(vector<struct TollZone>& tollZones, Encryptor& encryptor, Evaluator& evaluator, Decryptor& decryptor, CKKSEncoder& encoder, double scale);
vector<int> getRelevantTollZones(double gps_lat, double gps_lon, const struct SpatialIndex& spatial_index, Evaluator& evaluator, Encryptor& encryptor, Decryptor& decryptor, CKKSEncoder& encoder, double scale);
struct GeofenceResult checkLocationInGeofences(double gps_lat, double gps_lon, const struct TollZone& tollZone, Evaluator& evaluator, Encryptor& encryptor, Decryptor& decryptor, CKKSEncoder& encoder, double scale);

// Data structures - these will be defined in encryption_demo.cpp
// Just forward declare them here
struct GeofencePoint;
struct Geofence;
struct TollZone;
struct SpatialIndex;
struct EncryptedBoundingBox;
struct GeofenceResult;

// We need to include the actual implementations from encryption_demo.cpp
// but exclude the main function and avoid redefinition conflicts

// Include everything except main function
#define ENCRYPTION_DEMO_MAIN_GUARD
#include "encryption_demo.cpp"
#undef ENCRYPTION_DEMO_MAIN_GUARD

// Test fixture for SEAL context setup
class SEALTestFixture : public ::testing::Test {
protected:
    void SetUp() override {
        // Setup CKKS context for tests
        ckks_parms.set_poly_modulus_degree(8192);
        ckks_parms.set_coeff_modulus(CoeffModulus::Create(8192, {60, 40, 40, 60}));
        
        context = make_unique<SEALContext>(ckks_parms);
        keygen = make_unique<KeyGenerator>(*context);
        secret_key = keygen->secret_key();
        keygen->create_public_key(public_key);
        
        encryptor = make_unique<Encryptor>(*context, public_key);
        evaluator = make_unique<Evaluator>(*context);
        decryptor = make_unique<Decryptor>(*context, secret_key);
        encoder = make_unique<CKKSEncoder>(*context);
        
        scale = pow(2.0, 40);
    }

    EncryptionParameters ckks_parms{scheme_type::ckks};
    unique_ptr<SEALContext> context;
    unique_ptr<KeyGenerator> keygen;
    SecretKey secret_key;
    PublicKey public_key;
    unique_ptr<Encryptor> encryptor;
    unique_ptr<Evaluator> evaluator;
    unique_ptr<Decryptor> decryptor;
    unique_ptr<CKKSEncoder> encoder;
    double scale;
};

// Helper function to create test toll zone
TollZone createTestTollZone() {
    TollZone tz;
    tz.code = "TEST123";
    tz.name = "Test Zone";
    tz.highway = "TEST";
    tz.latitude = 38.0;
    tz.longitude = -9.0;
    tz.type = "CLOSED";
    
    Geofence gf;
    gf.geofencePoints = {
        {38.0, -9.0},
        {38.001, -9.001},
        {38.002, -9.002}
    };
    tz.geofences.push_back(gf);
    
    return tz;
}

// Test the trim function
TEST(UtilityFunctionsTest, TrimFunction) {
    EXPECT_EQ(trim("  hello world  "), "hello world");
    EXPECT_EQ(trim("no_spaces"), "no_spaces");
    EXPECT_EQ(trim("   "), "");
    EXPECT_EQ(trim(""), "");
    EXPECT_EQ(trim("start_space "), "start_space");
    EXPECT_EQ(trim(" end_space"), "end_space");
}

// Test JSON parsing functions
TEST(JsonParsingTest, ParseGeofencePoints) {
    string json = R"({"latitude":38.123,"longitude":-9.456},{"latitude":38.789,"longitude":-9.012})";
    auto points = parseGeofencePointsArray(json);
    
    ASSERT_EQ(points.size(), 2);
    EXPECT_DOUBLE_EQ(points[0].latitude, 38.123);
    EXPECT_DOUBLE_EQ(points[0].longitude, -9.456);
    EXPECT_DOUBLE_EQ(points[1].latitude, 38.789);
    EXPECT_DOUBLE_EQ(points[1].longitude, -9.012);
}

TEST(JsonParsingTest, ParseGeofencePointsEmpty) {
    string json = "";
    auto points = parseGeofencePointsArray(json);
    EXPECT_TRUE(points.empty());
}

TEST(JsonParsingTest, ParseSingleGeofence) {
    string json = R"({"geofencePoints":[{"latitude":38.0,"longitude":-9.0},{"latitude":38.1,"longitude":-9.1}]})";
    auto geofence = parseSingleGeofence(json);
    
    ASSERT_EQ(geofence.geofencePoints.size(), 2);
    EXPECT_DOUBLE_EQ(geofence.geofencePoints[0].latitude, 38.0);
    EXPECT_DOUBLE_EQ(geofence.geofencePoints[1].longitude, -9.1);
}

TEST(JsonParsingTest, ParseGeofencesArray) {
    string json = R"({"geofencePoints":[{"latitude":38.0,"longitude":-9.0}]},{"geofencePoints":[{"latitude":39.0,"longitude":-8.0}]})";
    auto geofences = parseGeofencesArray(json);
    
    ASSERT_EQ(geofences.size(), 2);
    ASSERT_EQ(geofences[0].geofencePoints.size(), 1);
    ASSERT_EQ(geofences[1].geofencePoints.size(), 1);
    EXPECT_DOUBLE_EQ(geofences[0].geofencePoints[0].latitude, 38.0);
    EXPECT_DOUBLE_EQ(geofences[1].geofencePoints[0].latitude, 39.0);
}

TEST(JsonParsingTest, ParseSingleTollZone) {
    string json = R"({
        "code":"1234",
        "name":"Test Zone",
        "highway":"A1",
        "latitude":38.123,
        "longitude":-9.456,
        "type":"CLOSED",
        "geofences":[{"geofencePoints":[{"latitude":38.0,"longitude":-9.0}]}]
    })";
    
    auto tollZone = parseSingleTollZone(json);
    
    EXPECT_EQ(tollZone.code, "1234");
    EXPECT_EQ(tollZone.name, "Test Zone");
    EXPECT_EQ(tollZone.highway, "A1");
    EXPECT_EQ(tollZone.type, "CLOSED");
    EXPECT_DOUBLE_EQ(tollZone.latitude, 38.123);
    EXPECT_DOUBLE_EQ(tollZone.longitude, -9.456);
    ASSERT_EQ(tollZone.geofences.size(), 1);
}

TEST(JsonParsingTest, LoadTollZonesFromFile) {
    // Create a test JSON file
    string testFilename = "test_tolls.json";
    ofstream testFile(testFilename);
    testFile << R"({
        "tollsList": [
            {
                "code":"TEST1",
                "name":"Test Zone 1",
                "highway":"A1",
                "latitude":38.0,
                "longitude":-9.0,
                "type":"CLOSED",
                "geofences":[{"geofencePoints":[{"latitude":38.0,"longitude":-9.0}]}]
            },
            {
                "code":"TEST2",
                "name":"Test Zone 2",
                "highway":"A2",
                "latitude":39.0,
                "longitude":-8.0,
                "type":"OPEN",
                "geofences":[{"geofencePoints":[{"latitude":39.0,"longitude":-8.0}]}]
            }
        ]
    })";
    testFile.close();
    
    auto tollZones = loadTollZonesFromJSON(testFilename);
    
    ASSERT_EQ(tollZones.size(), 2);
    EXPECT_EQ(tollZones[0].code, "TEST1");
    EXPECT_EQ(tollZones[1].code, "TEST2");
    
    // Clean up
    remove(testFilename.c_str());
}

TEST(JsonParsingTest, LoadTollZonesFromNonexistentFile) {
    auto tollZones = loadTollZonesFromJSON("nonexistent_file.json");
    EXPECT_TRUE(tollZones.empty());
}

// Test SEAL encryption functions
TEST_F(SEALTestFixture, PreEncryptCentroids) {
    vector<TollZone> tollZones = {createTestTollZone()};
    
    preEncryptCentroids(tollZones, *encryptor, *encoder, scale);
    
    ASSERT_FALSE(tollZones[0].encrypted_centroids.empty());
    EXPECT_EQ(tollZones[0].encrypted_centroids.size(), tollZones[0].geofences.size());
}

TEST_F(SEALTestFixture, BuildSpatialIndex) {
    vector<TollZone> tollZones = {createTestTollZone()};
    
    auto spatialIndex = buildSpatialIndex(tollZones, *encryptor, *evaluator, *decryptor, *encoder, scale);
    
    EXPECT_FALSE(spatialIndex.latitude_boundaries.empty());
    EXPECT_FALSE(spatialIndex.band_to_zones.empty());
}

TEST_F(SEALTestFixture, GetRelevantTollZones) {
    vector<TollZone> tollZones = {createTestTollZone()};
    auto spatialIndex = buildSpatialIndex(tollZones, *encryptor, *evaluator, *decryptor, *encoder, scale);
    
    auto relevantZones = getRelevantTollZones(38.0, -9.0, spatialIndex, *evaluator, *encryptor, *decryptor, *encoder, scale);
    
    // Should find at least some zones (the exact number depends on the spatial indexing)
    EXPECT_FALSE(relevantZones.empty());
}

TEST_F(SEALTestFixture, CheckLocationInGeofences) {
    TollZone tollZone = createTestTollZone();
    vector<TollZone> tollZones = {tollZone};
    preEncryptCentroids(tollZones, *encryptor, *encoder, scale);
    
    auto result = checkLocationInGeofences(38.0, -9.0, tollZones[0], *evaluator, *encryptor, *decryptor, *encoder, scale);
    
    // Should get a valid result (inside or outside)
    EXPECT_FALSE(result.message.empty());
    EXPECT_GE(result.geofenceIndex, -1);
}

// Edge case tests
TEST(EdgeCasesTest, EmptyGeofencePoints) {
    string json = "";
    auto points = parseGeofencePointsArray(json);
    EXPECT_TRUE(points.empty());
}

TEST(EdgeCasesTest, MalformedJson) {
    string json = "{ invalid json }";
    auto tollZone = parseSingleTollZone(json);
    EXPECT_TRUE(tollZone.code.empty());
}

TEST(EdgeCasesTest, ZeroCoordinates) {
    string json = R"({"latitude":0.0,"longitude":0.0})";
    auto points = parseGeofencePointsArray(json);
    // Note: The parser filters out (0.0, 0.0) coordinates as they're considered invalid
    EXPECT_TRUE(points.empty());
}

TEST(EdgeCasesTest, NonZeroCoordinates) {
    string json = R"({"latitude":0.1,"longitude":0.1})";
    auto points = parseGeofencePointsArray(json);
    ASSERT_EQ(points.size(), 1);
    EXPECT_DOUBLE_EQ(points[0].latitude, 0.1);
    EXPECT_DOUBLE_EQ(points[0].longitude, 0.1);
}

// Performance tests
TEST(PerformanceTest, LargeJsonParsing) {
    // Create a large JSON string with many points
    string json;
    for (int i = 0; i < 1000; ++i) {
        if (i > 0) json += ",";
        json += R"({"latitude":)" + to_string(38.0 + i * 0.001) + 
                R"(,"longitude":)" + to_string(-9.0 + i * 0.001) + "}";
    }
    
    auto start = chrono::high_resolution_clock::now();
    auto points = parseGeofencePointsArray(json);
    auto end = chrono::high_resolution_clock::now();
    
    EXPECT_EQ(points.size(), 1000);
    auto duration = chrono::duration_cast<chrono::milliseconds>(end - start);
    EXPECT_LT(duration.count(), 1000); // Should parse in less than 1 second
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
