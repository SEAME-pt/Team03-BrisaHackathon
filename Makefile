# Makefile for Team03-BrisaHackathon Privacy-Enhanced Homomorphic Toll Detection System
# Author: Team03-BrisaHackathon
# Description: Build system for SEAL-based encrypted toll detection

# Compiler settings
CXX = g++
CXXFLAGS = -std=c++17 -O2 -Wall -Wextra -pedantic
DEBUG_FLAGS = -g -DDEBUG -O0
RELEASE_FLAGS = -O3 -DNDEBUG -march=native

# SEAL library configuration
SEAL_INCLUDE = /usr/local/include/SEAL-4.1
SEAL_LIB = /usr/local/lib
SEAL_LIBS = -lseal-4.1

# Google Test configuration (for unit tests)
GTEST_INCLUDE = /opt/homebrew/include
GTEST_LIBS = -L/opt/homebrew/lib -lgtest -lgtest_main -lpthread

# Include and library paths
INCLUDES = -I$(SEAL_INCLUDE)
LIBS = -L$(SEAL_LIB) $(SEAL_LIBS)

# Source files
MAIN_SRC = encryption_demo.cpp
TEST_SRC = encryption_demo_test.cpp

# Target executables
MAIN_TARGET = gps_accuracy_demo_private
TEST_TARGET = encryption_demo_test
DEBUG_TARGET = gps_accuracy_demo_private_debug

# Input data files
DATA_FILE = tolls.json

# Default target
.PHONY: all
all: $(MAIN_TARGET)

# Main executable (release build)
$(MAIN_TARGET): $(MAIN_SRC)
	@echo "Building main executable (release)..."
	$(CXX) $(CXXFLAGS) $(INCLUDES) -o $@ $< $(LIBS)
	@echo "✅ Build completed: $@"

# Debug build
.PHONY: debug
debug: $(DEBUG_TARGET)

$(DEBUG_TARGET): $(MAIN_SRC)
	@echo "Building debug executable..."
	$(CXX) $(CXXFLAGS) $(DEBUG_FLAGS) $(INCLUDES) -o $@ $< $(LIBS)
	@echo "✅ Debug build completed: $@"

# Optimized release build
.PHONY: release
release: CXXFLAGS += $(RELEASE_FLAGS)
release: $(MAIN_TARGET)
	@echo "✅ Optimized release build completed"

# Unit tests (requires Google Test)
.PHONY: test
test: $(TEST_TARGET)
	@echo "Running unit tests..."
	./$(TEST_TARGET)

$(TEST_TARGET): $(TEST_SRC) $(MAIN_SRC)
	@echo "Building unit tests..."
	$(CXX) $(CXXFLAGS) $(INCLUDES) -I$(GTEST_INCLUDE) -o $@ $< $(LIBS) $(GTEST_LIBS)
	@echo "✅ Test build completed: $@"

# Run the main program
.PHONY: run
run: $(MAIN_TARGET)
	@echo "Running Privacy-Enhanced Homomorphic Toll Detection System..."
	@echo "================================================================"
	./$(MAIN_TARGET)

# Run with timing and profiling
.PHONY: profile
profile: $(MAIN_TARGET)
	@echo "Running with time profiling..."
	time ./$(MAIN_TARGET)

# Check if required dependencies are available
.PHONY: check-deps
check-deps:
	@echo "Checking dependencies..."
	@echo -n "SEAL library: "
	@if [ -f "$(SEAL_LIB)/libseal-4.1.so" ] || [ -f "$(SEAL_LIB)/libseal-4.1.dylib" ] || [ -f "$(SEAL_LIB)/libseal-4.1.a" ]; then \
		echo "✅ Found"; \
	else \
		echo "❌ Not found in $(SEAL_LIB)"; \
	fi
	@echo -n "SEAL headers: "
	@if [ -d "$(SEAL_INCLUDE)" ]; then \
		echo "✅ Found"; \
	else \
		echo "❌ Not found in $(SEAL_INCLUDE)"; \
	fi
	@echo -n "Data file: "
	@if [ -f "$(DATA_FILE)" ]; then \
		echo "✅ Found"; \
	else \
		echo "❌ Not found: $(DATA_FILE)"; \
	fi
	@echo -n "Google Test: "
	@if [ -f "/opt/homebrew/lib/libgtest.a" ] || [ -f "/opt/homebrew/lib/libgtest.so" ] || [ -f "/opt/homebrew/lib/libgtest.dylib" ]; then \
		echo "✅ Found"; \
	else \
		echo "❌ Not found (required for tests)"; \
	fi

# Install dependencies (macOS with Homebrew)
.PHONY: install-deps-mac
install-deps-mac:
	@echo "Installing dependencies on macOS..."
	brew install microsoft-seal
	brew install googletest

# Install dependencies (Ubuntu/Debian)
.PHONY: install-deps-ubuntu
install-deps-ubuntu:
	@echo "Installing dependencies on Ubuntu/Debian..."
	sudo apt-get update
	sudo apt-get install libseal-dev libgtest-dev

# Clean build artifacts
.PHONY: clean
clean:
	@echo "Cleaning build artifacts..."
	rm -f $(MAIN_TARGET) $(DEBUG_TARGET) $(TEST_TARGET)
	rm -f *.o *.a
	rm -f core core.*
	rm -f test_tolls.json
	@echo "✅ Clean completed"

# Create a release package
.PHONY: package
package: clean release
	@echo "Creating release package..."
	mkdir -p release
	cp $(MAIN_TARGET) release/
	cp $(DATA_FILE) release/ 2>/dev/null || echo "Warning: $(DATA_FILE) not found"
	cp README.md release/
	cp Makefile release/
	tar -czf team03-toll-detection-release.tar.gz release/
	rm -rf release/
	@echo "✅ Release package created: team03-toll-detection-release.tar.gz"

# Code analysis and linting
.PHONY: lint
lint:
	@echo "Running static analysis..."
	@if command -v cppcheck >/dev/null 2>&1; then \
		cppcheck --enable=all --std=c++17 $(MAIN_SRC); \
	else \
		echo "cppcheck not found, skipping static analysis"; \
	fi

# Performance benchmark
.PHONY: benchmark
benchmark: release
	@echo "Running performance benchmark..."
	@echo "Testing with multiple runs for average timing..."
	@for i in 1 2 3 4 5; do \
		echo "Run $$i:"; \
		time ./$(MAIN_TARGET) | grep "Average time per coordinate"; \
	done

# Memory check (requires valgrind)
.PHONY: memcheck
memcheck: debug
	@echo "Running memory check..."
	@if command -v valgrind >/dev/null 2>&1; then \
		valgrind --tool=memcheck --leak-check=full ./$(DEBUG_TARGET); \
	else \
		echo "valgrind not found, skipping memory check"; \
	fi

# Help target
.PHONY: help
help:
	@echo "Team03-BrisaHackathon Privacy-Enhanced Toll Detection Makefile"
	@echo "=============================================================="
	@echo ""
	@echo "Available targets:"
	@echo "  all          - Build main executable (default)"
	@echo "  debug        - Build debug version with symbols"
	@echo "  release      - Build optimized release version"
	@echo "  test         - Build and run unit tests"
	@echo "  run          - Build and run the main program"
	@echo "  profile      - Run with timing information"
	@echo "  benchmark    - Run performance benchmark"
	@echo "  clean        - Remove build artifacts"
	@echo "  package      - Create release package"
	@echo "  check-deps   - Check if dependencies are available"
	@echo "  install-deps-mac    - Install dependencies on macOS"
	@echo "  install-deps-ubuntu - Install dependencies on Ubuntu"
	@echo "  lint         - Run static code analysis"
	@echo "  memcheck     - Run memory leak detection"
	@echo "  help         - Show this help message"
	@echo ""
	@echo "Examples:"
	@echo "  make                 # Build main executable"
	@echo "  make run             # Build and run"
	@echo "  make test            # Build and run tests"
	@echo "  make release         # Optimized build"
	@echo "  make clean           # Clean artifacts"

# Additional build rules for different configurations
.PHONY: fast
fast: CXXFLAGS += -Ofast -march=native -mtune=native
fast: $(MAIN_TARGET)
	@echo "✅ Fast build completed (maximum optimization)"

.PHONY: small
small: CXXFLAGS += -Os
small: $(MAIN_TARGET)
	@echo "✅ Size-optimized build completed"

# Parallel build support
.PHONY: parallel
parallel:
	$(MAKE) -j$(shell nproc) all

# Print build configuration
.PHONY: config
config:
	@echo "Build Configuration:"
	@echo "==================="
	@echo "Compiler: $(CXX)"
	@echo "Flags: $(CXXFLAGS)"
	@echo "SEAL Include: $(SEAL_INCLUDE)"
	@echo "SEAL Library: $(SEAL_LIB)"
	@echo "SEAL Libs: $(SEAL_LIBS)"
	@echo "Source: $(MAIN_SRC)"
	@echo "Target: $(MAIN_TARGET)"
