terraform {
  required_version = ">= 1.13.5, < 2.0.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "8.25.0"
    }
  }
}

# Use the operator's external OCI profile/environment. Never put keys in tfvars.
provider "oci" {
  region = var.region
}
