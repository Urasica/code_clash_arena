variable "region" {
  description = "Approved OCI region; no deployment region is selected by default."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]+-[a-z0-9-]+-[0-9]+$", var.region))
    error_message = "Set an explicit OCI region identifier."
  }
}

variable "compartment_id" {
  description = "Approved compartment for this independent network environment."
  type        = string
  nullable    = false

  validation {
    condition     = can(regex("^ocid1\\.compartment\\.", var.compartment_id))
    error_message = "An explicit compartment OCID is required."
  }
}

variable "name_prefix" {
  description = "Short non-secret environment label, also used as the VCN DNS label."
  type        = string
  default     = "ccanet"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,11}$", var.name_prefix))
    error_message = "Use 2-12 lowercase letters/digits, starting with a letter."
  }
}

variable "vcn_cidr" {
  description = "Canonical RFC1918 IPv4 network; choose a range that does not overlap existing networks."
  type        = string
  default     = "10.42.0.0/16"
  nullable    = false

  validation {
    condition = try(
      cidrhost(var.vcn_cidr, 0) == split("/", var.vcn_cidr)[0] &&
      tonumber(split("/", var.vcn_cidr)[1]) >= 16 &&
      tonumber(split("/", var.vcn_cidr)[1]) <= 24 &&
      can(cidrnetmask(var.vcn_cidr)) &&
      can(regex("^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)", var.vcn_cidr)),
      false
    )
    error_message = "Use a canonical RFC1918 IPv4 CIDR with a /16 through /24 prefix."
  }
}

variable "subnet_newbits" {
  description = "Split the VCN into disjoint subnet 0 (public) and subnet 1 (private)."
  type        = number
  default     = 8
  nullable    = false

  validation {
    condition = try(
      var.subnet_newbits == floor(var.subnet_newbits) &&
      var.subnet_newbits >= 1 &&
      tonumber(split("/", var.vcn_cidr)[1]) + var.subnet_newbits <= 29,
      false
    )
    error_message = "Use a positive integer producing IPv4 subnets no smaller than /29."
  }
}

variable "bastion_client_cidrs" {
  description = "Explicit public IPv4 /32 addresses allowed to connect to short-lived Bastion sessions."
  type        = set(string)
  nullable    = false

  validation {
    condition = length(var.bastion_client_cidrs) > 0 && alltrue([
      for cidr in var.bastion_client_cidrs : try(
        can(cidrnetmask(cidr)) && split("/", cidr)[1] == "32" &&
        !can(regex("^(0\\.|10\\.|127\\.|169\\.254\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)", cidr)) &&
        tonumber(split(".", cidr)[0]) < 224,
        false
      )
    ])
    error_message = "Management access requires explicit public IPv4 /32 addresses, never an empty or broad allowlist."
  }
}

variable "allow_http_redirect" {
  description = "Opt in only when port 80 is needed for HTTPS redirect or ACME HTTP-01."
  type        = bool
  default     = false
  nullable    = false
}

variable "data_access_mode" {
  description = "Policy variant, not VM placement: host_local (no DB network rules) or private_vnic (application-to-data NSGs)."
  type        = string
  default     = "host_local"
  nullable    = false

  validation {
    condition     = contains(["host_local", "private_vnic"], var.data_access_mode)
    error_message = "Use host_local or private_vnic. Neither mode provisions or attaches a VM."
  }
}

variable "block_nat_traffic" {
  description = "Controlled outbound-denial test switch; changing this needs explicit environment approval."
  type        = bool
  default     = false
  nullable    = false
}
