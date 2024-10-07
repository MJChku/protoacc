# Compiler
CXX = g++

# Directories
SRC_DIRS = src/tsim src/dpi
INCLUDE_DIR = include/
BUILD_DIR = build_cpp
verilator_build_dir = build/verilator

# Source files
# SRC_FILES = $(wildcard src/tsim/*.cc src/dpi/*.cc ./main.cc)
SRC_FILES = $(wildcard src/tsim/*.cc src/dpi/*.cc src/vmem/*.cc)

# Object files
OBJ_FILES = $(patsubst %.cc,$(BUILD_DIR)/%.o,$(notdir $(SRC_FILES)))
# Compiler flags
INCLUDES = -I$(INCLUDE_DIR)
INCLUDES += -I/usr/local/share/verilator/include/vltstd
INCLUDES += -I/usr/local/share/verilator/include
CXXFLAGS = -Wall -O2 $(INCLUDES) -g 
# The final executable
TARGET = libprotoacc_tsim.so
# Linker flags for building a shared library
LDFLAGS = -shared -ldl -lpthread

# Rule to build the target executable
$(TARGET): $(BUILD_DIR) $(OBJ_FILES)
	$(CXX) $(OBJ_FILES) -o $@ $(LDFLAGS)

# Create build directory if it doesn't exist
$(BUILD_DIR):
	mkdir -p $(BUILD_DIR)

# Rule to compile each .cc file into an .o file, but store it in the build directory
$(BUILD_DIR)/%.o: src/tsim/%.cc
	$(CXX) $(CXXFLAGS) -fPIC -c $< -o $@

$(BUILD_DIR)/%.o: src/dpi/%.cc
	$(CXX) $(CXXFLAGS) -fPIC -c $< -o $@

$(BUILD_DIR)/%.o: src/vmem/%.cc
	$(CXX) $(CXXFLAGS) -fPIC -c $< -o $@


$(BUILD_DIR)/main.o: ./main.cc
	$(CXX) $(CXXFLAGS) -fPIC -c $< -o $@

# Clean up the build directory and executable
.PHONY: clean
clean:
	rm -rf $(BUILD_DIR) $(TARGET)
