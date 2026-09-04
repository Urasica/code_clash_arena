resource "oci_core_network_security_group_security_rule" "edge_https" {
  network_security_group_id = oci_core_network_security_group.role["edge"].id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  stateless                 = false
  description               = "Public HTTPS and WSS entrypoint"
  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "edge_http" {
  count                     = var.allow_http_redirect ? 1 : 0
  network_security_group_id = oci_core_network_security_group.role["edge"].id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  stateless                 = false
  description               = "Opt-in redirect or ACME HTTP-01 only"
  tcp_options {
    destination_port_range {
      min = 80
      max = 80
    }
  }
}

resource "oci_core_network_security_group_security_rule" "edge_to_application" {
  network_security_group_id = oci_core_network_security_group.role["edge"].id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination               = oci_core_network_security_group.role["application"].id
  destination_type          = "NETWORK_SECURITY_GROUP"
  stateless                 = false
  description               = "Verified TLS to private API ingress; no direct Spring port"
  tcp_options {
    destination_port_range {
      min = 8443
      max = 8443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "application_from_edge" {
  network_security_group_id = oci_core_network_security_group.role["application"].id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = oci_core_network_security_group.role["edge"].id
  source_type               = "NETWORK_SECURITY_GROUP"
  stateless                 = false
  description               = "Private TLS API ingress from Edge only"
  tcp_options {
    destination_port_range {
      min = 8443
      max = 8443
    }
  }
}

# This permits HTTPS destinations, not a domain allowlist. DNS/IP filtering
# and host-level restrictions are separate deployment controls.
resource "oci_core_network_security_group_security_rule" "https_outbound" {
  for_each                  = local.active_host_roles
  network_security_group_id = oci_core_network_security_group.role[each.key].id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  stateless                 = false
  description               = "HTTPS registry, updates and identity provider access"
  tcp_options {
    destination_port_range {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_network_security_group_security_rule" "bastion_ssh" {
  for_each                  = local.active_host_roles
  network_security_group_id = oci_core_network_security_group.role[each.key].id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "${oci_bastion_bastion.management.private_endpoint_ip_address}/32"
  source_type               = "CIDR_BLOCK"
  stateless                 = false
  description               = "SSH from Bastion private endpoint only"
  tcp_options {
    destination_port_range {
      min = 22
      max = 22
    }
  }
}

# IPv4 path-MTU discovery, not a general ICMP/ping or transport ingress rule.
resource "oci_core_network_security_group_security_rule" "path_mtu" {
  for_each                  = local.active_host_roles
  network_security_group_id = oci_core_network_security_group.role[each.key].id
  direction                 = "INGRESS"
  protocol                  = "1"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  stateless                 = false
  description               = "IPv4 fragmentation-needed messages only"
  icmp_options {
    type = 3
    code = 4
  }
}

locals {
  data_ports = var.data_access_mode == "private_vnic" ? { mysql = 3306, redis = 6379 } : {}
}

resource "oci_core_network_security_group_security_rule" "application_to_data" {
  for_each                  = local.data_ports
  network_security_group_id = oci_core_network_security_group.role["application"].id
  direction                 = "EGRESS"
  protocol                  = "6"
  destination               = oci_core_network_security_group.role["data"].id
  destination_type          = "NETWORK_SECURITY_GROUP"
  stateless                 = false
  description               = "Application to separate ${each.key} VNIC only"
  tcp_options {
    destination_port_range {
      min = each.value
      max = each.value
    }
  }
}

resource "oci_core_network_security_group_security_rule" "data_from_application" {
  for_each                  = local.data_ports
  network_security_group_id = oci_core_network_security_group.role["data"].id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = oci_core_network_security_group.role["application"].id
  source_type               = "NETWORK_SECURITY_GROUP"
  stateless                 = false
  description               = "Separate ${each.key} VNIC accepts Application NSG only"
  tcp_options {
    destination_port_range {
      min = each.value
      max = each.value
    }
  }
}
