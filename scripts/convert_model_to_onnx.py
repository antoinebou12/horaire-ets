#!/usr/bin/env python3
"""
One-time script to convert sentence-transformers model to ONNX format.

This script converts the paraphrase-multilingual-MiniLM-L12-v2 model
from sentence-transformers format to ONNX format for use in Java applications.

Requirements:
    pip install sentence-transformers[onnx] optimum[onnxruntime]
    
    Or for CPU-only:
    pip install sentence-transformers[onnx] optimum[onnxruntime]
    
    Or for GPU support:
    pip install sentence-transformers[onnx-gpu] optimum[onnxruntime-gpu]
"""

import os
import sys
from pathlib import Path


def convert_model_to_onnx():
    """
    Convert sentence-transformers model to ONNX format.
    
    Model: paraphrase-multilingual-MiniLM-L12-v2
    Output: src/main/resources/models/paraphrase-multilingual-MiniLM-L12-v2.onnx
    """
    
    # Model configuration
    # Try with sentence-transformers prefix first, fallback to model name only
    model_name_full = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"
    model_name = "paraphrase-multilingual-MiniLM-L12-v2"
    
    # Paths
    script_dir = Path(__file__).parent.absolute()
    project_root = script_dir.parent
    output_dir = project_root / "src" / "main" / "resources" / "models"
    temp_models_dir = project_root / "models"
    
    # Create output directories
    output_dir.mkdir(parents=True, exist_ok=True)
    temp_models_dir.mkdir(parents=True, exist_ok=True)
    
    # Use the base model name (without prefix) for the output filename
    output_path = output_dir / f"{model_name}.onnx"
    temp_output_path = temp_models_dir / f"{model_name}.onnx"
    
    print(f"Converting model: {model_name_full}")
    print(f"Output location: {output_path}")
    print("-" * 60)
    
    try:
        # Method 1: Using optimum library (most reliable and straightforward)
        try:
            print("Attempting conversion using optimum library...")
            from optimum.onnxruntime import ORTModelForFeatureExtraction
            from transformers import AutoTokenizer
            import shutil
            
            print(f"Loading model: {model_name_full}...")
            
            # Convert to ONNX using optimum - this exports directly
            # Try with full path first, fallback to short name if needed
            try:
                print("Converting to ONNX format...")
                ort_model = ORTModelForFeatureExtraction.from_pretrained(
                    model_name_full,
                    export=True,
                    provider="CPUExecutionProvider"
                )
            except Exception as e:
                print(f"Trying with model name without prefix due to: {e}")
                ort_model = ORTModelForFeatureExtraction.from_pretrained(
                    model_name,
                    export=True,
                    provider="CPUExecutionProvider"
                )
            
            # Save to temporary location
            temp_model_dir = temp_models_dir / model_name
            print(f"Saving ONNX model to temporary location...")
            ort_model.save_pretrained(str(temp_model_dir))
            
            # Save tokenizer as well (may be needed for inference)
            try:
                tokenizer = AutoTokenizer.from_pretrained(model_name_full)
            except:
                tokenizer = AutoTokenizer.from_pretrained(model_name)
            tokenizer.save_pretrained(str(temp_model_dir))
            
            # Find the ONNX model file
            onnx_file = temp_model_dir / "model.onnx"
            if not onnx_file.exists():
                # Search for any .onnx file in the directory
                onnx_files = list(temp_model_dir.rglob("*.onnx"))
                if onnx_files:
                    onnx_file = onnx_files[0]
                else:
                    raise FileNotFoundError(
                        f"ONNX model file not found in {temp_model_dir}"
                    )
            
            print(f"Found ONNX model at: {onnx_file}")
            
            # Copy to final location
            shutil.copy2(onnx_file, output_path)
            print(f"✓ Model successfully converted and saved to: {output_path}")
            
        except ImportError:
            print("optimum library not available, trying sentence-transformers with ONNX backend...")
            
            # Method 2: Using sentence-transformers with ONNX backend
            from sentence_transformers import SentenceTransformer
            import shutil
            import os
            
            print(f"Loading sentence-transformers model with ONNX backend: {model_name_full}...")
            print("Note: This will export the model to ONNX format automatically...")
            
            # Load with ONNX backend - this automatically exports to ONNX
            # sentence-transformers accepts model names with or without prefix
            model = SentenceTransformer(model_name_full, backend="onnx")
            
            # Run a test encode to ensure export is complete
            print("Triggering ONNX export with test encoding...")
            _ = model.encode(["test"], convert_to_numpy=True)
            
            # Find where sentence-transformers stored the ONNX model
            # It's typically in the cache directory
            onnx_file = None
            
            # Try to access the model's internal path
            try:
                # sentence-transformers stores ONNX models in cache
                # Try to find it via the model's internal structure
                if hasattr(model, '_modules') and len(model._modules) > 0:
                    first_module = list(model._modules.values())[0]
                    if hasattr(first_module, 'model_path'):
                        model_path = Path(first_module.model_path)
                        potential_onnx = model_path / "onnx" / "model.onnx"
                        if potential_onnx.exists():
                            onnx_file = potential_onnx
            except Exception as e:
                print(f"Could not access model path directly: {e}")
            
            # If not found, search in common cache locations
            if onnx_file is None or not onnx_file.exists():
                print("Searching for ONNX model in cache directories...")
                import glob
                
                # Common cache locations
                search_patterns = [
                    os.path.expanduser(f"~/.cache/huggingface/**/{model_name_full.replace('/', '--')}/**/*.onnx"),
                    os.path.expanduser(f"~/.cache/sentence_transformers/**/{model_name_full.replace('/', '_')}/**/*.onnx"),
                    os.path.expanduser(f"~/.cache/huggingface/**/{model_name.replace('/', '--')}/**/*.onnx"),
                    os.path.expanduser(f"~/.cache/sentence_transformers/**/{model_name.replace('/', '_')}/**/*.onnx"),
                ]
                
                for pattern in search_patterns:
                    matches = glob.glob(pattern, recursive=True)
                    if matches:
                        # Prefer 'model.onnx' files
                        for match in matches:
                            if 'model.onnx' in match or match.endswith('.onnx'):
                                onnx_file = Path(match)
                                break
                    if onnx_file and onnx_file.exists():
                        break
            
            if onnx_file is None or not onnx_file.exists():
                raise FileNotFoundError(
                    "Could not locate the exported ONNX model file. "
                    "The model was exported but we couldn't find it in the cache."
                )
            
            print(f"Found ONNX model at: {onnx_file}")
            
            # Copy to final location
            shutil.copy2(onnx_file, output_path)
            print(f"✓ Model successfully converted and saved to: {output_path}")
            
        except ImportError:
            print("sentence-transformers not available, trying optimum library...")
            
            # Method 2: Using optimum library as fallback
            from optimum.onnxruntime import ORTModelForFeatureExtraction
            from transformers import AutoTokenizer
            
            print(f"Loading model: {model_name}...")
            tokenizer = AutoTokenizer.from_pretrained(model_name)
            
            # Convert to ONNX using optimum
            print("Converting to ONNX format...")
            ort_model = ORTModelForFeatureExtraction.from_pretrained(
                model_name,
                export=True
            )
            
            # Save to temporary location
            print(f"Saving ONNX model to temporary location...")
            ort_model.save_pretrained(str(temp_models_dir / model_name))
            
            # Find the ONNX model file
            onnx_file = temp_models_dir / model_name / "model.onnx"
            if not onnx_file.exists():
                onnx_files = list((temp_models_dir / model_name).rglob("*.onnx"))
                if onnx_files:
                    onnx_file = onnx_files[0]
                else:
                    raise FileNotFoundError("ONNX model file not found after conversion")
            
            # Copy to final location
            import shutil
            shutil.copy2(onnx_file, output_path)
            print(f"✓ Model successfully converted and saved to: {output_path}")
        
        # Clean up temporary directory
        if temp_models_dir.exists() and temp_models_dir.is_dir():
            try:
                import shutil
                shutil.rmtree(temp_models_dir)
                print(f"✓ Cleaned up temporary directory: {temp_models_dir}")
            except Exception as e:
                print(f"Warning: Could not clean up temporary directory: {e}")
        
        print("-" * 60)
        print("Conversion completed successfully!")
        print(f"ONNX model saved at: {output_path}")
        print(f"File size: {output_path.stat().st_size / (1024*1024):.2f} MB")
        
    except ImportError as e:
        print(f"\n✗ Error: Required library not installed: {e}")
        print("\nPlease install required dependencies:")
        print("  pip install sentence-transformers[onnx] optimum[onnxruntime]")
        print("\nOr for CPU-only:")
        print("  pip install sentence-transformers[onnx] optimum[onnxruntime]")
        print("\nOr for GPU support:")
        print("  pip install sentence-transformers[onnx-gpu] optimum[onnxruntime-gpu]")
        sys.exit(1)
        
    except Exception as e:
        print(f"\n✗ Error during conversion: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    print("=" * 60)
    print("Sentence-Transformers to ONNX Converter")
    print("=" * 60)
    convert_model_to_onnx()
