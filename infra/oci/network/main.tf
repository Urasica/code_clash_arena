locals {
  subnet_cidrs = {
    public  = cidrsubnet(var.vcn_cidr, var.subnet_newbits, 0)
    private = cidrsubnet(var.vcn_cidr, var.subnet_newbits, 1)
  }
  tags = {
    project     = "code-clash-arena"
    environment = var.name_prefix
    managed_by  = "terraform"
  }
  active_host_roles = toset(var.data_access_mode == "private_vnic" ? ["edge", "application", "data"] : ["edge", "application"])
}

resource "oci_core_vcn" "network" {
  compartment_id = var.compartment_id
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "${var.name_prefix}-vcn"
  dns_label      = var.name_prefix
  is_ipv6enabled = false
  freeform_tags  = local.tags
}

resource "oci_core_internet_gateway" "public" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-igw"
  enabled        = true
  freeform_tags  = local.tags
}

resource "oci_core_nat_gateway" "private" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-nat"
  block_traffic  = var.block_nat_traffic
  freeform_tags  = local.tags
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-public"
  freeform_tags  = local.tags

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.public.id
  }
}

resource "oci_core_route_table" "private" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-private"
  freeform_tags  = local.tags

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_nat_gateway.private.id
  }
}

resource "oci_core_dhcp_options" "network" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-dns"
  freeform_tags  = local.tags

  options {
    type        = "DomainNameServer"
    server_type = "VcnLocalPlusInternet"
  }
}

# Never attach OCI's permissive default security list to these subnets.
resource "oci_core_security_list" "public" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-public-baseline"
  freeform_tags  = local.tags
}

resource "oci_core_security_list" "private" {
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-private-baseline"
  freeform_tags  = local.tags

  # Bastion's private endpoint cannot be assigned our host NSGs. Only SSH
  # egress to these two subnets is shared; target ingress still requires its /32.
  dynamic "egress_security_rules" {
    for_each = local.subnet_cidrs
    content {
      destination      = egress_security_rules.value
      destination_type = "CIDR_BLOCK"
      protocol         = "6"
      stateless        = false
      description      = "Bastion endpoint SSH to ${egress_security_rules.key} targets"
      tcp_options {
        min = 22
        max = 22
      }
    }
  }
}

resource "oci_core_subnet" "network" {
  for_each                   = local.subnet_cidrs
  compartment_id             = var.compartment_id
  vcn_id                     = oci_core_vcn.network.id
  cidr_block                 = each.value
  display_name               = "${var.name_prefix}-${each.key}"
  dns_label                  = each.key
  prohibit_public_ip_on_vnic = each.key == "private"
  route_table_id             = each.key == "public" ? oci_core_route_table.public.id : oci_core_route_table.private.id
  security_list_ids          = [each.key == "public" ? oci_core_security_list.public.id : oci_core_security_list.private.id]
  dhcp_options_id            = oci_core_dhcp_options.network.id
  freeform_tags              = local.tags
}

resource "oci_core_network_security_group" "role" {
  for_each       = toset(["edge", "application", "data"])
  compartment_id = var.compartment_id
  vcn_id         = oci_core_vcn.network.id
  display_name   = "${var.name_prefix}-${each.key}"
  freeform_tags  = local.tags
}

resource "oci_bastion_bastion" "management" {
  bastion_type                 = "standard"
  compartment_id               = var.compartment_id
  target_subnet_id             = oci_core_subnet.network["private"].id
  name                         = "${var.name_prefix}bastion"
  client_cidr_block_allow_list = sort(tolist(var.bastion_client_cidrs))
  max_session_ttl_in_seconds   = 1800
  dns_proxy_status             = "DISABLED"
  freeform_tags                = local.tags
}
