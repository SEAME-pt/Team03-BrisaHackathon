#include <iostream>
#include <vector>
#include <iomanip>
#include <cmath>
#include "seal/seal.h"

using namespace seal;
using namespace std;

int main() {
    cout << "CKKS Noise Analysis for GPS Toll Detection" << endl;
    cout << "==========================================" << endl;
    
    // Setup same CKKS context as main program
    EncryptionParameters ckks_parms(scheme_type::ckks);
    ckks_parms.set_poly_modulus_degree(16384);
    ckks_parms.set_coeff_modulus(CoeffModulus::Create(16384, {60, 50, 50, 50, 60}));

    SEALContext ckks_context(ckks_parms);
    KeyGenerator ckks_keygen(ckks_context);
    SecretKey ckks_secret_key = ckks_keygen.secret_key();
    PublicKey ckks_public_key;
    ckks_keygen.create_public_key(ckks_public_key);

    Encryptor ckks_encryptor(ckks_context, ckks_public_key);
    Evaluator ckks_evaluator(ckks_context);
    Decryptor ckks_decryptor(ckks_context, ckks_secret_key);
    CKKSEncoder ckks_encoder(ckks_context);

    double scale = pow(2.0, 50);
    
    // Test GPS coordinates (Portugal)
    double test_lat = 38.65676812;  // Pinhal Novo
    double test_lon = -8.89353369;
    double centroid_lat = 38.65676800;  // Very close centroid (1.2cm difference)
    double centroid_lon = -8.89353350;
    
    cout << fixed << setprecision(10);
    cout << "Test Coordinates:" << endl;
    cout << "GPS:      (" << test_lat << ", " << test_lon << ")" << endl;
    cout << "Centroid: (" << centroid_lat << ", " << centroid_lon << ")" << endl;
    
    // Calculate true distance
    double true_lat_diff = test_lat - centroid_lat;
    double true_lon_diff = test_lon - centroid_lon;
    double true_distance_sq = true_lat_diff * true_lat_diff + true_lon_diff * true_lon_diff;
    
    cout << "\nTrue Calculations:" << endl;
    cout << "Lat diff:     " << scientific << true_lat_diff << endl;
    cout << "Lon diff:     " << scientific << true_lon_diff << endl;
    cout << "Distance²:    " << scientific << true_distance_sq << endl;
    
    // Convert to meters for reference
    double lat_diff_meters = true_lat_diff * 111320;  // degrees to meters
    double lon_diff_meters = true_lon_diff * 86600;   // at Portugal latitude
    double distance_meters = sqrt(lat_diff_meters*lat_diff_meters + lon_diff_meters*lon_diff_meters);
    
    cout << "Distance:     " << fixed << setprecision(2) << distance_meters << " meters" << endl;
    
    // Perform homomorphic computation
    cout << "\nHomomorphic Computation:" << endl;
    
    // Encrypt coordinates
    Plaintext plain_gps_lat, plain_gps_lon, plain_cent_lat, plain_cent_lon;
    ckks_encoder.encode(test_lat, scale, plain_gps_lat);
    ckks_encoder.encode(test_lon, scale, plain_gps_lon);
    ckks_encoder.encode(centroid_lat, scale, plain_cent_lat);
    ckks_encoder.encode(centroid_lon, scale, plain_cent_lon);
    
    Ciphertext enc_gps_lat, enc_gps_lon, enc_cent_lat, enc_cent_lon;
    ckks_encryptor.encrypt(plain_gps_lat, enc_gps_lat);
    ckks_encryptor.encrypt(plain_gps_lon, enc_gps_lon);
    ckks_encryptor.encrypt(plain_cent_lat, enc_cent_lat);
    ckks_encryptor.encrypt(plain_cent_lon, enc_cent_lon);
    
    // Homomorphic distance calculation
    Ciphertext lat_diff_enc, lon_diff_enc;
    ckks_evaluator.sub(enc_gps_lat, enc_cent_lat, lat_diff_enc);
    ckks_evaluator.sub(enc_gps_lon, enc_cent_lon, lon_diff_enc);
    
    Ciphertext lat_diff_sq, lon_diff_sq;
    ckks_evaluator.square(lat_diff_enc, lat_diff_sq);
    ckks_evaluator.square(lon_diff_enc, lon_diff_sq);
    
    ckks_evaluator.rescale_to_next_inplace(lat_diff_sq);
    ckks_evaluator.rescale_to_next_inplace(lon_diff_sq);
    
    // Align scales
    parms_id_type last_parms_id = lat_diff_sq.parms_id();
    ckks_evaluator.mod_switch_to_inplace(lon_diff_sq, last_parms_id);
    lon_diff_sq.scale() = lat_diff_sq.scale();
    
    Ciphertext distance_sq_enc;
    ckks_evaluator.add(lat_diff_sq, lon_diff_sq, distance_sq_enc);
    
    // Decrypt results
    Plaintext lat_diff_plain, lon_diff_plain, distance_sq_plain;
    ckks_decryptor.decrypt(lat_diff_enc, lat_diff_plain);
    ckks_decryptor.decrypt(lon_diff_enc, lon_diff_plain);
    ckks_decryptor.decrypt(distance_sq_enc, distance_sq_plain);
    
    vector<double> computed_lat_diff, computed_lon_diff, computed_distance_sq;
    ckks_encoder.decode(lat_diff_plain, computed_lat_diff);
    ckks_encoder.decode(lon_diff_plain, computed_lon_diff);
    ckks_encoder.decode(distance_sq_plain, computed_distance_sq);
    
    cout << "Computed lat diff:  " << scientific << computed_lat_diff[0] << endl;
    cout << "Computed lon diff:  " << scientific << computed_lon_diff[0] << endl;
    cout << "Computed distance²: " << scientific << computed_distance_sq[0] << endl;
    
    // Calculate errors
    double lat_error = abs(computed_lat_diff[0] - true_lat_diff);
    double lon_error = abs(computed_lon_diff[0] - true_lon_diff);
    double dist_error = abs(computed_distance_sq[0] - true_distance_sq);
    
    cout << "\nEncryption Errors:" << endl;
    cout << "Lat diff error:   " << scientific << lat_error << " degrees" << endl;
    cout << "Lon diff error:   " << scientific << lon_error << " degrees" << endl;
    cout << "Distance² error:  " << scientific << dist_error << " degrees²" << endl;
    
    // Convert to GPS error in meters
    double lat_error_meters = lat_error * 111320;
    double lon_error_meters = lon_error * 86600;
    double dist_error_meters = sqrt(dist_error) * sqrt(111320.0 * 86600.0);
    
    cout << "\nGPS Error in Meters:" << endl;
    cout << "Lat error:     " << scientific << lat_error_meters << " meters" << endl;
    cout << "Lon error:     " << scientific << lon_error_meters << " meters" << endl;
    cout << "Distance error:" << scientific << dist_error_meters << " meters" << endl;
    
    // Relative errors
    cout << "\nRelative Errors:" << endl;
    cout << "Lat relative:    " << scientific << lat_error / abs(true_lat_diff) * 100 << "%" << endl;
    cout << "Lon relative:    " << scientific << lon_error / abs(true_lon_diff) * 100 << "%" << endl;
    cout << "Distance relative: " << scientific << dist_error / true_distance_sq * 100 << "%" << endl;
    
    // Noise budget analysis
    cout << "\nNoise Budget Analysis:" << endl;
    cout << "Scale:           2^" << log2(scale) << " = " << scientific << scale << endl;
    cout << "Remaining scale: 2^" << log2(distance_sq_enc.scale()) << " = " << scientific << distance_sq_enc.scale() << endl;
    cout << "Noise budget:    " << fixed << setprecision(1) << (log2(distance_sq_enc.scale()) / log2(scale) * 100) << "% remaining" << endl;
    
    // Comparison with detection threshold
    double threshold = 0.00000036;  // 60 meters
    cout << "\nThreshold Comparison:" << endl;
    cout << "Detection threshold: " << scientific << threshold << " degrees²" << endl;
    cout << "Error vs threshold:  " << scientific << dist_error / threshold * 100 << "%" << endl;
    
    if (dist_error < threshold * 1e-6) {
        cout << "✅ EXCELLENT: Error is " << scientific << threshold / dist_error << "x smaller than threshold" << endl;
    } else if (dist_error < threshold * 1e-3) {
        cout << "✅ GOOD: Error is acceptable for toll detection" << endl;
    } else {
        cout << "⚠️  WARNING: Error may affect detection accuracy" << endl;
    }
    
    return 0;
}
