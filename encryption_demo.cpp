#include <iostream>
#include <vector>
#include <iomanip>
#include <cmath>
#include <fstream>
#include <string>
#include <chrono>
#include "seal/seal.h"

using namespace seal;
using namespace std;

// Simplified JSON parsing functions
string trim(const string& str) {
    size_t first = str.find_first_not_of(' ');
    if (first == string::npos) return "";
    size_t last = str.find_last_not_of(' ');
    return str.substr(first, (last - first + 1));
}

// Core data structures (simplified)
struct TestLocation {
    double lat, lon;
    string description;
};

struct GeofencePoint {
    double latitude;
    double longitude;
};

struct Geofence {
    vector<GeofencePoint> geofencePoints;
};

struct EncryptedBoundingBox {
    Ciphertext min_lat, max_lat, min_lon, max_lon;
    double privacy_radius;
};

// SIMD-packed toll zone batch for parallel processing
struct SIMDTollBatch {
    vector<int> toll_indices;           // Original toll zone indices
    vector<string> toll_codes;          // Corresponding toll codes
    Ciphertext packed_lat_centroids;    // SIMD-packed latitude centroids
    Ciphertext packed_lon_centroids;    // SIMD-packed longitude centroids
    size_t batch_size;                  // Number of toll zones in this batch
};

// Simplified spatial index with SIMD optimization
struct SpatialIndex {
    vector<EncryptedBoundingBox> latitude_boundaries;  // For binary search
    vector<vector<int>> band_to_zones;                 // Maps latitude bands to toll zones
    vector<vector<SIMDTollBatch>> simd_batches;        // SIMD-packed toll batches per band
};

struct TollZone {
    string code;
    string name;
    string highway;
    double latitude;
    double longitude;
    vector<Geofence> geofences;
    string type;
    vector<pair<Ciphertext, Ciphertext>> encrypted_centroids;
    int latitude_band_id;
    int simd_slot_index;  // Index within SIMD-packed ciphertext
};

struct GeofenceResult {
    bool isInside;
    string tollCode;
    int geofenceIndex;
    string message;
};

// Function declarations
vector<TollZone> loadTollZonesFromJSON(const string& filename);
TollZone parseSingleTollZone(const string& json);
vector<Geofence> parseGeofencesArray(const string& json);
Geofence parseSingleGeofence(const string& json);
vector<GeofencePoint> parseGeofencePointsArray(const string& json);
void preEncryptCentroids(vector<TollZone>& tollZones, Encryptor& encryptor, CKKSEncoder& encoder, double scale);
vector<SIMDTollBatch> createSIMDBatches(const vector<int>& toll_indices, const vector<TollZone>& tollZones, Encryptor& encryptor, CKKSEncoder& encoder, double scale);
SpatialIndex buildSpatialIndex(vector<TollZone>& tollZones, Encryptor& encryptor, Evaluator& evaluator, Decryptor& decryptor, CKKSEncoder& encoder, double scale);
vector<int> getRelevantTollZones(double gps_lat, double gps_lon, const SpatialIndex& spatial_index, Evaluator& evaluator, Encryptor& encryptor, Decryptor& decryptor, CKKSEncoder& encoder, double scale);
vector<GeofenceResult> checkLocationInGeofencesSIMD(double gps_lat, double gps_lon, const SIMDTollBatch& batch, const vector<TollZone>& tollZones, Evaluator& evaluator, Encryptor& encryptor, Decryptor& decryptor, CKKSEncoder& encoder, double scale);
GeofenceResult checkLocationInGeofences(double gps_lat, double gps_lon, const TollZone& tollZone, Evaluator& evaluator, Encryptor& encryptor, Decryptor& decryptor, CKKSEncoder& encoder, double scale);

// Load toll zones from JSON file  
vector<TollZone> loadTollZonesFromJSON(const string& filename) {
    vector<TollZone> tollZones;
    
    cout << "Loading toll zones from " << filename << "..." << endl;
    
    ifstream file(filename);
    if (!file.is_open()) {
        cout << "Error: Could not open " << filename << endl;
        return tollZones;
    }
    
    string content((istreambuf_iterator<char>(file)), istreambuf_iterator<char>());
    file.close();
    
    size_t tollsListStart = content.find("\"tollsList\":");
    if (tollsListStart == string::npos) {
        cout << "Error: tollsList not found in JSON" << endl;
        return tollZones;
    }
    
    size_t arrayStart = content.find("[", tollsListStart);
    if (arrayStart == string::npos) {
        cout << "Error: tollsList array not found" << endl;
        return tollZones;
    }
    
    size_t pos = arrayStart + 1;
    
    while (pos < content.length()) {
        while (pos < content.length() && (content[pos] == ' ' || content[pos] == '\t' || 
               content[pos] == '\n' || content[pos] == '\r' || content[pos] == ',')) {
            pos++;
        }
        
        if (pos >= content.length() || content[pos] == ']') break;
        if (content[pos] != '{') { pos++; continue; }
        
        size_t objStart = pos;
        int braceLevel = 1;
        size_t objEnd = objStart + 1;
        bool inString = false;
        char prevChar = '{';
        
        while (objEnd < content.length() && braceLevel > 0) {
            char c = content[objEnd];
            if (c == '"' && prevChar != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braceLevel++;
                else if (c == '}') braceLevel--;
            }
            prevChar = c;
            objEnd++;
        }
        
        string objectJson = content.substr(objStart, objEnd - objStart);
        
        if (objectJson.find("\"code\":") != string::npos) {
            TollZone tollZone = parseSingleTollZone(objectJson);
            if (!tollZone.code.empty() && !tollZone.geofences.empty() && 
                tollZone.code.find("500") != 0 && tollZone.code.find("999") != 0) {
                tollZones.push_back(tollZone);
            }
        }
        
        pos = objEnd;
    }
    
    cout << "Loaded " << tollZones.size() << " toll zones" << endl;
    return tollZones;
}

TollZone parseSingleTollZone(const string& json) {
    TollZone tollZone;
    
    // Extract code
    size_t codePos = json.find("\"code\":");
    if (codePos != string::npos) {
        size_t quoteStart = json.find('"', codePos + 7);
        size_t quoteEnd = json.find('"', quoteStart + 1);
        if (quoteStart != string::npos && quoteEnd != string::npos) {
            tollZone.code = json.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
        }
    }
    
    // Extract name
    size_t namePos = json.find("\"name\":");
    if (namePos != string::npos) {
        size_t quoteStart = json.find('"', namePos + 7);
        size_t quoteEnd = json.find('"', quoteStart + 1);
        if (quoteStart != string::npos && quoteEnd != string::npos) {
            tollZone.name = json.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
        }
    }
    
    // Extract highway
    size_t hwPos = json.find("\"highway\":");
    if (hwPos != string::npos) {
        size_t quoteStart = json.find('"', hwPos + 10);
        size_t quoteEnd = json.find('"', quoteStart + 1);
        if (quoteStart != string::npos && quoteEnd != string::npos) {
            tollZone.highway = json.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
        }
    }
    
    // Extract latitude
    size_t latPos = json.find("\"latitude\":");
    if (latPos != string::npos) {
        size_t numStart = latPos + 11;
        size_t numEnd = json.find_first_of(",}", numStart);
        if (numEnd != string::npos) {
            string latStr = trim(json.substr(numStart, numEnd - numStart));
            if (!latStr.empty()) tollZone.latitude = stod(latStr);
        }
    }
    
    // Extract longitude
    size_t lonPos = json.find("\"longitude\":");
    if (lonPos != string::npos) {
        size_t numStart = lonPos + 12;
        size_t numEnd = json.find_first_of(",}", numStart);
        if (numEnd != string::npos) {
            string lonStr = trim(json.substr(numStart, numEnd - numStart));
            if (!lonStr.empty()) tollZone.longitude = stod(lonStr);
        }
    }
    
    // Extract type
    size_t typePos = json.find("\"type\":");
    if (typePos != string::npos) {
        size_t quoteStart = json.find('"', typePos + 7);
        size_t quoteEnd = json.find('"', quoteStart + 1);
        if (quoteStart != string::npos && quoteEnd != string::npos) {
            tollZone.type = json.substr(quoteStart + 1, quoteEnd - quoteStart - 1);
        }
    }
    
    // Extract geofences
    size_t geofencesPos = json.find("\"geofences\":");
    if (geofencesPos != string::npos) {
        size_t arrayStart = json.find("[", geofencesPos);
        if (arrayStart != string::npos) {
            int bracketLevel = 1;
            size_t arrayPos = arrayStart + 1;
            bool inString = false;
            char prevChar = '[';
            
            while (arrayPos < json.length() && bracketLevel > 0) {
                char c = json[arrayPos];
                if (c == '"' && prevChar != '\\') inString = !inString;
                if (!inString) {
                    if (c == '[') bracketLevel++;
                    else if (c == ']') bracketLevel--;
                }
                prevChar = c;
                arrayPos++;
            }
            
            string geofencesJson = json.substr(arrayStart + 1, arrayPos - arrayStart - 2);
            tollZone.geofences = parseGeofencesArray(geofencesJson);
        }
    }
    
    return tollZone;
}

vector<Geofence> parseGeofencesArray(const string& json) {
    vector<Geofence> geofences;
    if (json.empty() || json.find_first_not_of(" \t\n\r") == string::npos) return geofences;
    
    size_t pos = 0;
    while (pos < json.length()) {
        size_t objStart = json.find("{", pos);
        if (objStart == string::npos) break;
        
        int braceLevel = 1;
        size_t objPos = objStart + 1;
        bool inString = false;
        char prevChar = '{';
        
        while (objPos < json.length() && braceLevel > 0) {
            char c = json[objPos];
            if (c == '"' && prevChar != '\\') inString = !inString;
            if (!inString) {
                if (c == '{') braceLevel++;
                else if (c == '}') braceLevel--;
            }
            prevChar = c;
            objPos++;
        }
        
        string geofenceJson = json.substr(objStart, objPos - objStart);
        Geofence geofence = parseSingleGeofence(geofenceJson);
        
        if (!geofence.geofencePoints.empty()) {
            geofences.push_back(geofence);
        }
        
        pos = objPos;
    }
    
    return geofences;
}

Geofence parseSingleGeofence(const string& json) {
    Geofence geofence;
    
    size_t pointsPos = json.find("\"geofencePoints\":");
    if (pointsPos != string::npos) {
        size_t arrayStart = json.find("[", pointsPos);
        if (arrayStart != string::npos) {
            int bracketLevel = 1;
            size_t arrayPos = arrayStart + 1;
            bool inString = false;
            char prevChar = '[';
            
            while (arrayPos < json.length() && bracketLevel > 0) {
                char c = json[arrayPos];
                if (c == '"' && prevChar != '\\') inString = !inString;
                if (!inString) {
                    if (c == '[') bracketLevel++;
                    else if (c == ']') bracketLevel--;
                }
                prevChar = c;
                arrayPos++;
            }
            
            string pointsJson = json.substr(arrayStart + 1, arrayPos - arrayStart - 2);
            geofence.geofencePoints = parseGeofencePointsArray(pointsJson);
        }
    }
    
    return geofence;
}

vector<GeofencePoint> parseGeofencePointsArray(const string& json) {
    vector<GeofencePoint> points;
    if (json.empty() || json.find_first_not_of(" \t\n\r") == string::npos) return points;
    
    size_t pos = 0;
    while (pos < json.length()) {
        size_t objStart = json.find("{", pos);
        if (objStart == string::npos) break;
        
        size_t objEnd = json.find("}", objStart);
        if (objEnd == string::npos) break;
        
        string pointJson = json.substr(objStart, objEnd - objStart + 1);
        GeofencePoint point;
        
        // Extract latitude
        size_t latPos = pointJson.find("\"latitude\":");
        if (latPos != string::npos) {
            size_t numStart = latPos + 11;
            size_t numEnd = pointJson.find_first_of(",}", numStart);
            if (numEnd != string::npos) {
                string latStr = trim(pointJson.substr(numStart, numEnd - numStart));
                if (!latStr.empty()) point.latitude = stod(latStr);
            }
        }
        
        // Extract longitude
        size_t lonPos = pointJson.find("\"longitude\":");
        if (lonPos != string::npos) {
            size_t numStart = lonPos + 12;
            size_t numEnd = pointJson.find_first_of(",}", numStart);
            if (numEnd != string::npos) {
                string lonStr = trim(pointJson.substr(numStart, numEnd - numStart));
                if (!lonStr.empty()) point.longitude = stod(lonStr);
            }
        }
        
        if (point.latitude != 0.0 || point.longitude != 0.0) {
            points.push_back(point);
        }
        
        pos = objEnd + 1;
    }
    
    return points;
}

// Create SIMD-packed batches for parallel toll zone processing
vector<SIMDTollBatch> createSIMDBatches(const vector<int>& toll_indices, 
                                        const vector<TollZone>& tollZones,
                                        Encryptor& encryptor, CKKSEncoder& encoder, double scale) {
    vector<SIMDTollBatch> batches;
    const size_t SIMD_BATCH_SIZE = 8;  // Process 8 toll zones in parallel
    
    for (size_t i = 0; i < toll_indices.size(); i += SIMD_BATCH_SIZE) {
        SIMDTollBatch batch;
        size_t batch_end = min(i + SIMD_BATCH_SIZE, toll_indices.size());
        batch.batch_size = batch_end - i;
        
        // Collect toll zone data for this batch
        vector<double> lat_centroids, lon_centroids;
        for (size_t j = i; j < batch_end; j++) {
            int toll_idx = toll_indices[j];
            batch.toll_indices.push_back(toll_idx);
            batch.toll_codes.push_back(tollZones[toll_idx].code);
            
            // For simplicity, use first geofence centroid
            if (!tollZones[toll_idx].geofences.empty()) {
                const auto& geofence = tollZones[toll_idx].geofences[0];
                double centroid_lat = 0, centroid_lon = 0;
                
                for (const auto& point : geofence.geofencePoints) {
                    centroid_lat += point.latitude;
                    centroid_lon += point.longitude;
                }
                centroid_lat /= geofence.geofencePoints.size();
                centroid_lon /= geofence.geofencePoints.size();
                
                lat_centroids.push_back(centroid_lat);
                lon_centroids.push_back(centroid_lon);
            }
        }
        
        // Pad with zeros if batch is not full
        while (lat_centroids.size() < SIMD_BATCH_SIZE) {
            lat_centroids.push_back(0.0);
            lon_centroids.push_back(0.0);
        }
        
        // Create SIMD-packed ciphertexts
        Plaintext plain_lat_batch, plain_lon_batch;
        encoder.encode(lat_centroids, scale, plain_lat_batch);
        encoder.encode(lon_centroids, scale, plain_lon_batch);
        
        encryptor.encrypt(plain_lat_batch, batch.packed_lat_centroids);
        encryptor.encrypt(plain_lon_batch, batch.packed_lon_centroids);
        
        batches.push_back(batch);
    }
    
    return batches;
}

// Build simplified spatial index with SIMD optimization
SpatialIndex buildSpatialIndex(vector<TollZone>& tollZones, 
                              Encryptor& encryptor, Evaluator& evaluator,
                              Decryptor& decryptor, CKKSEncoder& encoder, double scale) {
    cout << "Building spatial index..." << endl;
    
    SpatialIndex index;
    
    // Latitude boundaries for binary search
    vector<double> latitude_lines = {
        37.0529959999999952, 38.1679199999999967, 38.6606671200000023, 38.7783333300000013,
        38.9366590000000025, 39.2592164299999984, 39.4927777800000026, 39.6586739999999985,
        39.9023405900000027, 40.1986227400000008, 40.5093333300000014, 40.7078160000000033,
        40.9788888899999982, 41.2311111100000013, 41.5397497200000017, 41.9787167000000033
    };
    
    // Create encrypted latitude boundaries
    vector<Ciphertext> encrypted_boundaries;
    for (double lat : latitude_lines) {
        Plaintext plain_boundary;
        encoder.encode(lat, scale, plain_boundary);
        Ciphertext enc_boundary;
        encryptor.encrypt(plain_boundary, enc_boundary);
        encrypted_boundaries.push_back(enc_boundary);
    }
    
    // Assign toll zones to latitude bands
    vector<vector<int>> latitude_bands(latitude_lines.size() - 1);
    
    for (size_t t = 0; t < tollZones.size(); t++) {
        auto& tollZone = tollZones[t];
        
        Plaintext plain_toll_lat;
        encoder.encode(tollZone.latitude, scale, plain_toll_lat);
        Ciphertext enc_toll_lat;
        encryptor.encrypt(plain_toll_lat, enc_toll_lat);
        
        bool assigned = false;
        for (size_t b = 0; b < latitude_bands.size(); b++) {
            Ciphertext comp_min, comp_max;
            evaluator.sub(enc_toll_lat, encrypted_boundaries[b], comp_min);
            evaluator.sub(encrypted_boundaries[b + 1], enc_toll_lat, comp_max);
            
            Plaintext min_plain, max_plain;
            decryptor.decrypt(comp_min, min_plain);
            decryptor.decrypt(comp_max, max_plain);
            
            vector<double> min_val, max_val;
            encoder.decode(min_plain, min_val);
            encoder.decode(max_plain, max_val);
            
            if (min_val[0] >= 0 && max_val[0] > 0) {
                latitude_bands[b].push_back(t);
                tollZone.latitude_band_id = b;
                assigned = true;
                break;
            }
        }
        
        if (!assigned) {
            latitude_bands.back().push_back(t);
            tollZone.latitude_band_id = latitude_bands.size() - 1;
        }
    }
    
    // Store encrypted boundaries in index
    for (const auto& enc_boundary : encrypted_boundaries) {
        EncryptedBoundingBox enc_bbox;
        enc_bbox.privacy_radius = 0.001;
        enc_bbox.min_lat = enc_boundary;
        enc_bbox.max_lat = enc_boundary;
        
        Plaintext zero_plain;
        encoder.encode(0.0, scale, zero_plain);
        encryptor.encrypt(zero_plain, enc_bbox.min_lon);
        encryptor.encrypt(zero_plain, enc_bbox.max_lon);
        
        index.latitude_boundaries.push_back(enc_bbox);
    }
    
    index.band_to_zones = latitude_bands;
    
    // Create SIMD batches for each latitude band
    index.simd_batches.resize(latitude_bands.size());
    for (size_t band = 0; band < latitude_bands.size(); band++) {
        if (!latitude_bands[band].empty()) {
            index.simd_batches[band] = createSIMDBatches(
                latitude_bands[band], tollZones, encryptor, encoder, scale);
        }
    }
    
    cout << "Index built: " << latitude_lines.size() << " boundaries, " 
         << latitude_bands.size() << " bands, " << tollZones.size() << " toll zones" << endl;
    cout << "SIMD optimization: Created batches for parallel processing" << endl;
    
    return index;
}

// Get relevant toll zones using binary search
vector<int> getRelevantTollZones(double gps_lat, double gps_lon,
                                const SpatialIndex& spatial_index,
                                Evaluator& evaluator, Encryptor& encryptor, 
                                Decryptor& decryptor, CKKSEncoder& encoder, double scale) {
    
    // Note: gps_lon is not used in current implementation as spatial index only uses latitude bands
    (void)gps_lon;  // Suppress unused parameter warning
    
    vector<int> relevant_zones;
    
    Plaintext plain_gps_lat;
    encoder.encode(gps_lat, scale, plain_gps_lat);
    Ciphertext enc_gps_lat;
    encryptor.encrypt(plain_gps_lat, enc_gps_lat);
    
    // Binary search to find latitude band
    int left = 0;
    int right = spatial_index.latitude_boundaries.size() - 2;
    int target_band = -1;
    
    while (left <= right) {
        int mid = left + (right - left) / 2;
        
        Ciphertext comparison_low, comparison_high;
        evaluator.sub(enc_gps_lat, spatial_index.latitude_boundaries[mid].min_lat, comparison_low);
        evaluator.sub(spatial_index.latitude_boundaries[mid + 1].min_lat, enc_gps_lat, comparison_high);
        
        Plaintext low_plain, high_plain;
        decryptor.decrypt(comparison_low, low_plain);
        decryptor.decrypt(comparison_high, high_plain);
        
        vector<double> low_val, high_val;
        encoder.decode(low_plain, low_val);
        encoder.decode(high_plain, high_val);
        
        bool gps_above_low = low_val[0] >= 0;
        bool gps_below_high = high_val[0] > 0;
        
        if (gps_above_low && gps_below_high) {
            target_band = mid;
            break;
        } else if (!gps_above_low) {
            right = mid - 1;
        } else {
            left = mid + 1;
        }
    }
    
    if (target_band >= 0 && target_band < static_cast<int>(spatial_index.band_to_zones.size())) {
        relevant_zones = spatial_index.band_to_zones[target_band];
    }
    
    return relevant_zones;
}

// Pre-encrypt centroids only (removed bounding boxes for better privacy)
void preEncryptCentroids(vector<TollZone>& tollZones, 
                        Encryptor& encryptor, CKKSEncoder& encoder, double scale) {
    cout << "Pre-encrypting centroids..." << endl;
    
    int total_centroids = 0;
    for (auto& tollZone : tollZones) {
        tollZone.encrypted_centroids.clear();
        
        for (size_t g = 0; g < tollZone.geofences.size(); g++) {
            const auto& geofence = tollZone.geofences[g];
            double centroid_lat = 0, centroid_lon = 0;
            
            for (const auto& point : geofence.geofencePoints) {
                centroid_lat += point.latitude;
                centroid_lon += point.longitude;
            }
            centroid_lat /= geofence.geofencePoints.size();
            centroid_lon /= geofence.geofencePoints.size();
            
            // Encrypt centroid
            Plaintext plain_centroid_lat, plain_centroid_lon;
            encoder.encode(centroid_lat, scale, plain_centroid_lat);
            encoder.encode(centroid_lon, scale, plain_centroid_lon);
            
            Ciphertext enc_centroid_lat, enc_centroid_lon;
            encryptor.encrypt(plain_centroid_lat, enc_centroid_lat);
            encryptor.encrypt(plain_centroid_lon, enc_centroid_lon);
            
            tollZone.encrypted_centroids.push_back({enc_centroid_lat, enc_centroid_lon});
            total_centroids++;
        }
    }
    
    cout << "Pre-encrypted " << total_centroids << " centroids for " << tollZones.size() << " toll zones" << endl;
}

// SIMD-optimized geofence checking - process multiple toll zones in parallel
vector<GeofenceResult> checkLocationInGeofencesSIMD(double gps_lat, double gps_lon,
                                                    const SIMDTollBatch& batch,
                                                    const vector<TollZone>& tollZones,
                                                    Evaluator& evaluator, Encryptor& encryptor, 
                                                    Decryptor& decryptor, CKKSEncoder& encoder,
                                                    double scale) {
    // Suppress unused parameter warning
    (void)tollZones;
    
    vector<GeofenceResult> results;
    
    // Encrypt GPS coordinates as SIMD vector (replicated across all slots)
    vector<double> gps_lat_vec(8, gps_lat);   // 8 slots for SIMD
    vector<double> gps_lon_vec(8, gps_lon);
    
    Plaintext plain_gps_lat, plain_gps_lon;
    encoder.encode(gps_lat_vec, scale, plain_gps_lat);
    encoder.encode(gps_lon_vec, scale, plain_gps_lon);
    
    Ciphertext enc_gps_lat, enc_gps_lon;
    encryptor.encrypt(plain_gps_lat, enc_gps_lat);
    encryptor.encrypt(plain_gps_lon, enc_gps_lon);
    
    // SIMD homomorphic distance calculation for all toll zones in parallel
    Ciphertext lat_diff_batch, lon_diff_batch;
    evaluator.sub(enc_gps_lat, batch.packed_lat_centroids, lat_diff_batch);
    evaluator.sub(enc_gps_lon, batch.packed_lon_centroids, lon_diff_batch);
    
    Ciphertext lat_diff_sq_batch, lon_diff_sq_batch;
    evaluator.square(lat_diff_batch, lat_diff_sq_batch);
    evaluator.square(lon_diff_batch, lon_diff_sq_batch);
    
    evaluator.rescale_to_next_inplace(lat_diff_sq_batch);
    evaluator.rescale_to_next_inplace(lon_diff_sq_batch);
    
    // Align scales and parameter levels
    parms_id_type last_parms_id = lat_diff_sq_batch.parms_id();
    evaluator.mod_switch_to_inplace(lon_diff_sq_batch, last_parms_id);
    lon_diff_sq_batch.scale() = lat_diff_sq_batch.scale();
    
    Ciphertext distance_sq_batch;
    evaluator.add(lat_diff_sq_batch, lon_diff_sq_batch, distance_sq_batch);
    
    // Threshold comparison with SIMD vector
    double primary_threshold = 0.00000036;  // ~60 meters
    vector<double> threshold_vec(8, primary_threshold);
    
    Plaintext threshold_plain;
    encoder.encode(threshold_vec, distance_sq_batch.scale(), threshold_plain);
    Ciphertext threshold_batch;
    encryptor.encrypt(threshold_plain, threshold_batch);
    
    evaluator.mod_switch_to_inplace(threshold_batch, distance_sq_batch.parms_id());
    threshold_batch.scale() = distance_sq_batch.scale();
    
    Ciphertext comparison_batch;
    evaluator.sub(threshold_batch, distance_sq_batch, comparison_batch);
    
    // Decrypt and decode SIMD results
    Plaintext comparison_plain;
    decryptor.decrypt(comparison_batch, comparison_plain);
    vector<double> comparison_results;
    encoder.decode(comparison_plain, comparison_results);
    
    // Process results for each toll zone in the batch
    for (size_t i = 0; i < batch.batch_size; i++) {
        GeofenceResult result;
        result.isInside = comparison_results[i] > 1e-8;  // Apply tolerance
        result.tollCode = batch.toll_codes[i];
        result.geofenceIndex = 0;  // First geofence for simplicity
        
        if (result.isInside) {
            result.message = "Inside geofence (SIMD batch processing)";
        } else {
            result.message = "Outside geofence (SIMD batch processing)";
        }
        
        results.push_back(result);
    }
    
    return results;
}

GeofenceResult checkLocationInGeofences(double gps_lat, double gps_lon,
                                       const TollZone& tollZone,
                                       Evaluator& evaluator, Encryptor& encryptor, 
                                       Decryptor& decryptor, CKKSEncoder& encoder,
                                       double scale) {
    
    GeofenceResult result;
    result.isInside = false;
    result.tollCode = "";
    result.geofenceIndex = -1;
    result.message = "Outside all geofences";
    
    // Encrypt GPS coordinates once
    Plaintext plain_lat, plain_lon;
    encoder.encode(gps_lat, scale, plain_lat);
    encoder.encode(gps_lon, scale, plain_lon);
    
    Ciphertext enc_lat, enc_lon;
    encryptor.encrypt(plain_lat, enc_lat);
    encryptor.encrypt(plain_lon, enc_lon);
    
    int geofences_checked = 0;
    
    // Check each geofence directly with centroid distance (no bounding box filtering)
    for (size_t g = 0; g < tollZone.geofences.size(); g++) {
        geofences_checked++;
        
        // Homomorphic distance calculation
        Ciphertext lat_diff_enc, lon_diff_enc;
        evaluator.sub(enc_lat, tollZone.encrypted_centroids[g].first, lat_diff_enc);
        evaluator.sub(enc_lon, tollZone.encrypted_centroids[g].second, lon_diff_enc);
        
        Ciphertext lat_diff_sq, lon_diff_sq;
        evaluator.square(lat_diff_enc, lat_diff_sq);
        evaluator.square(lon_diff_enc, lon_diff_sq);
        
        evaluator.rescale_to_next_inplace(lat_diff_sq);
        evaluator.rescale_to_next_inplace(lon_diff_sq);
        
        parms_id_type last_parms_id = lat_diff_sq.parms_id();
        evaluator.mod_switch_to_inplace(lon_diff_sq, last_parms_id);
        lon_diff_sq.scale() = lat_diff_sq.scale();
        
        Ciphertext distance_sq_enc;
        evaluator.add(lat_diff_sq, lon_diff_sq, distance_sq_enc);
        
        // Threshold comparison with stability enhancement
        // Use two thresholds to create a stability buffer against CKKS precision drift
        double primary_threshold = 0.00000036;  // ~60 meters
        double buffer_threshold = 0.00000049;   // ~70 meters for borderline cases
        
        // Primary threshold check
        Plaintext threshold_plain;
        encoder.encode(primary_threshold, distance_sq_enc.scale(), threshold_plain);
        Ciphertext threshold_enc;
        encryptor.encrypt(threshold_plain, threshold_enc);
        
        evaluator.mod_switch_to_inplace(threshold_enc, distance_sq_enc.parms_id());
        threshold_enc.scale() = distance_sq_enc.scale();
        
        Ciphertext comparison_result;
        evaluator.sub(threshold_enc, distance_sq_enc, comparison_result);
        
        Plaintext comparison_plain;
        decryptor.decrypt(comparison_result, comparison_plain);
        vector<double> comparison_val;
        encoder.decode(comparison_plain, comparison_val);
        
        double comparison_value = comparison_val[0];
        
        // If clearly inside primary threshold, accept
        bool inside_this_geofence = comparison_value > 1e-8;
        
        // If borderline, do a secondary check with buffer threshold for stability
        if (!inside_this_geofence && comparison_value > -2e-8) {
            Plaintext buffer_plain;
            encoder.encode(buffer_threshold, distance_sq_enc.scale(), buffer_plain);
            Ciphertext buffer_enc;
            encryptor.encrypt(buffer_plain, buffer_enc);
            
            evaluator.mod_switch_to_inplace(buffer_enc, distance_sq_enc.parms_id());
            buffer_enc.scale() = distance_sq_enc.scale();
            
            Ciphertext buffer_comparison;
            evaluator.sub(buffer_enc, distance_sq_enc, buffer_comparison);
            
            Plaintext buffer_comparison_plain;
            decryptor.decrypt(buffer_comparison, buffer_comparison_plain);
            vector<double> buffer_comparison_val;
            encoder.decode(buffer_comparison_plain, buffer_comparison_val);
            
            // For borderline cases, use buffer threshold with higher tolerance
            inside_this_geofence = buffer_comparison_val[0] > -1e-7;
        }
        
        if (inside_this_geofence) {
            result.isInside = true;
            result.geofenceIndex = static_cast<int>(g);
            result.message = "Inside geofence " + to_string(g + 1) + 
                           " (checked: " + to_string(geofences_checked) + " geofences)";
            result.tollCode = tollZone.code;
            break;
        }
    }
    
    if (!result.isInside) {
        result.message = "Outside all geofences (checked: " + to_string(geofences_checked) + " geofences)";
    }
    
    return result;
}

#ifndef ENCRYPTION_DEMO_MAIN_GUARD
int main() {
    cout << "Privacy-Enhanced Homomorphic Toll Detection System" << endl;
    cout << "==================================================" << endl;
    
    auto program_start = chrono::high_resolution_clock::now();

    try {
        // Setup CKKS context
        EncryptionParameters ckks_parms(scheme_type::ckks);
        ckks_parms.set_poly_modulus_degree(8192);
        ckks_parms.set_coeff_modulus(CoeffModulus::Create(8192, {60, 40, 40, 60}));

        SEALContext ckks_context(ckks_parms);
        KeyGenerator ckks_keygen(ckks_context);
        SecretKey ckks_secret_key = ckks_keygen.secret_key();
        PublicKey ckks_public_key;
        ckks_keygen.create_public_key(ckks_public_key);

        Encryptor ckks_encryptor(ckks_context, ckks_public_key);
        Evaluator ckks_evaluator(ckks_context);
        Decryptor ckks_decryptor(ckks_context, ckks_secret_key);
        CKKSEncoder ckks_encoder(ckks_context);

        double scale = pow(2.0, 40);

        // Load toll zones
        cout << "Loading toll zones from tolls.json..." << endl;
        vector<TollZone> tollZones = loadTollZonesFromJSON("tolls.json");
        
        if (tollZones.empty()) {
            cout << "No toll zones loaded. Using fallback data." << endl;
            TollZone tollZone;
            tollZone.code = "1212";
            tollZone.name = "Pinhal Novo 2";
            tollZone.highway = "A12";
            tollZone.latitude = 38.65451852;
            tollZone.longitude = -8.897964775;
            tollZone.type = "CLOSED";
            
            Geofence geofence1;
            geofence1.geofencePoints = {
                {38.656802634221954, -8.89406437912976},
                {38.65691740970802, -8.894013417158478},
                {38.656892826425825, -8.893697380457288},
                {38.65683269761516, -8.893382684860626},
                {38.656654748051714, -8.892980353508392},
                {38.6565523326305, -8.893029974375168},
                {38.65671265854521, -8.893387817297363},
                {38.656779677079534, -8.893713473711378}
            };
            
            Geofence geofence2;
            geofence2.geofencePoints = {
                {38.65583349226638, -8.897226703558358},
                {38.65595099723018, -8.897295099888238},
                {38.656222034711185, -8.896606700096507},
                {38.65632040137139, -8.89634049085178},
                {38.65635063854005, -8.89621643868484},
                {38.65627633512204, -8.896184252176662}
            };
            
            tollZone.geofences = {geofence1, geofence2};
            tollZones.push_back(tollZone);
        }
        
        cout << "Loaded " << tollZones.size() << " toll zones" << endl;
        
        // Pre-encrypt centroids (no bounding boxes for better privacy)
        preEncryptCentroids(tollZones, ckks_encryptor, ckks_encoder, scale);

        // Build spatial index
        SpatialIndex spatial_index = buildSpatialIndex(tollZones, ckks_encryptor, ckks_evaluator, ckks_decryptor, ckks_encoder, scale);

        // Test GPS locations using EXACT centroid coordinates from encryption
        vector<TestLocation> test_locations = {
            {38.65676812, -8.89353369, "Pinhal Novo 2, Fence 1 (EXACT centroid)"},
            {38.65615898, -8.89664495, "Pinhal Novo 2, Fence 2 (EXACT centroid)"},
            {38.82052119, -9.18781516, "Odivelas (close to centroid)"},
            {38.89223341, -9.04816278, "Alverca (close to centroid)"},
            {38.74311485, -9.27516933, "Queluz 1 (close to centroid)"},
            {40.57061698, -8.56225855, "Aveiro Sul (close to centroid)"},
            {38.660000, -8.890000, "Far outside (unchanged)"},
            {38.650000, -8.900000, "South (unchanged)"}
        };

        cout << "\nGPS Test Coordinates:" << endl;
        cout << "=====================" << endl;
        for (size_t i = 0; i < test_locations.size(); i++) {
            cout << "Test " << (i + 1) << ": " << test_locations[i].description 
                 << " -> (" << fixed << setprecision(8) 
                 << test_locations[i].lat << ", " << test_locations[i].lon << ")" << endl;
        }

        cout << "\nTesting GPS Locations:" << endl;
        cout << "=====================" << endl;

        auto total_checking_start = chrono::high_resolution_clock::now();

        for (size_t i = 0; i < test_locations.size(); i++) {
            const auto& location = test_locations[i];
            
            cout << "\nTest " << (i + 1) << ": " << location.description << endl;
            
            auto coord_start = chrono::high_resolution_clock::now();
            
            // Binary search to get relevant toll zones
            auto filter_start = chrono::high_resolution_clock::now();
            vector<int> relevant_toll_indices = getRelevantTollZones(
                location.lat, location.lon, spatial_index,
                ckks_evaluator, ckks_encryptor, ckks_decryptor, ckks_encoder, scale
            );
            auto filter_end = chrono::high_resolution_clock::now();
            auto filter_duration = chrono::duration_cast<chrono::milliseconds>(filter_end - filter_start);
            
            // Check relevant toll zones with SIMD optimization
            auto precision_start = chrono::high_resolution_clock::now();
            vector<GeofenceResult> results;
            
            // Get SIMD batches for the relevant latitude band
            int target_band = -1;
            for (size_t b = 0; b < spatial_index.band_to_zones.size(); b++) {
                for (int zone_idx : spatial_index.band_to_zones[b]) {
                    if (find(relevant_toll_indices.begin(), relevant_toll_indices.end(), zone_idx) != relevant_toll_indices.end()) {
                        target_band = b;
                        break;
                    }
                }
                if (target_band != -1) break;
            }
            
            // Check if any relevant toll zones have multiple geofences
            bool has_multi_geofence_zones = false;
            for (int toll_idx : relevant_toll_indices) {
                if (tollZones[toll_idx].geofences.size() > 1) {
                    has_multi_geofence_zones = true;
                    break;
                }
            }
            
            if (target_band != -1 && target_band < static_cast<int>(spatial_index.simd_batches.size()) && !has_multi_geofence_zones) {
                // Use SIMD-optimized processing only for single-geofence zones
                for (const auto& batch : spatial_index.simd_batches[target_band]) {
                    auto batch_results = checkLocationInGeofencesSIMD(
                        location.lat, location.lon, batch, tollZones,
                        ckks_evaluator, ckks_encryptor, ckks_decryptor, ckks_encoder, scale
                    );
                    
                    // Filter results to only include toll zones in relevant_toll_indices
                    for (const auto& result : batch_results) {
                        for (size_t i = 0; i < batch.toll_indices.size(); i++) {
                            if (find(relevant_toll_indices.begin(), relevant_toll_indices.end(), 
                                    batch.toll_indices[i]) != relevant_toll_indices.end()) {
                                results.push_back(result);
                                break;
                            }
                        }
                    }
                }
            } else {
                // Use sequential processing for multi-geofence zones or fallback
                for (int toll_idx : relevant_toll_indices) {
                    GeofenceResult result = checkLocationInGeofences(
                        location.lat, location.lon, tollZones[toll_idx],
                        ckks_evaluator, ckks_encryptor, ckks_decryptor, ckks_encoder, scale
                    );
                    result.message = tollZones[toll_idx].name + " (" + tollZones[toll_idx].code + ")";
                    results.push_back(result);
                }
            }
            auto precision_end = chrono::high_resolution_clock::now();
            auto precision_duration = chrono::duration_cast<chrono::milliseconds>(precision_end - precision_start);
            
            auto coord_end = chrono::high_resolution_clock::now();
            auto coord_duration = chrono::duration_cast<chrono::milliseconds>(coord_end - coord_start);
            
            // Count positive matches
            int positiveMatches = 0;
            vector<string> matchedTolls;
            
            for (const auto& result : results) {
                if (result.isInside) {
                    positiveMatches++;
                    matchedTolls.push_back(result.tollCode);
                }
            }
            
            // Results
            cout << "  Binary search: " << filter_duration.count() << "ms" << endl;
            cout << "  Precision checks: " << precision_duration.count() << "ms" << endl;
            cout << "  Total time: " << coord_duration.count() << "ms" << endl;
            cout << "  Zones filtered: " << (tollZones.size() - relevant_toll_indices.size()) 
                 << "/" << tollZones.size() << " (" 
                 << fixed << setprecision(1) << (100.0 * (tollZones.size() - relevant_toll_indices.size()) / tollZones.size()) 
                 << "% reduction)" << endl;
            
            if (positiveMatches > 0) {
                cout << "  Result: TOLL DETECTED (" << positiveMatches << " zones)" << endl;
                for (const auto& toll : matchedTolls) {
                    cout << "    Toll: " << toll << endl;
                }
            } else {
                cout << "  Result: NO TOLL" << endl;
            }
        }
        
        auto total_checking_end = chrono::high_resolution_clock::now();
        auto total_checking_duration = chrono::duration_cast<chrono::milliseconds>(total_checking_end - total_checking_start);
        
        cout << "\nTotal checking time: " << total_checking_duration.count() << " ms" << endl;
        cout << "Average time per coordinate: " << (total_checking_duration.count() / test_locations.size()) << " ms" << endl;

    } catch (const exception& e) {
        cout << "Error: " << e.what() << endl;
        return 1;
    }
    
    auto program_end = chrono::high_resolution_clock::now();
    auto program_duration = chrono::duration_cast<chrono::milliseconds>(program_end - program_start);
    
    cout << "\nProgram completed successfully!" << endl;
    cout << "Total execution time: " << program_duration.count() << " ms (" 
         << fixed << setprecision(2) << (program_duration.count() / 1000.0) << " seconds)" << endl;

    return 0;
}
#endif // ENCRYPTION_DEMO_MAIN_GUARD
