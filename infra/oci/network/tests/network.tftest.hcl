# All runs use a mock OCI provider, including command=apply. No cloud calls,
# credentials or remote state are used. These are configuration tests only.
mock_provider "oci" {
  mock_resource "oci_bastion_bastion" {
    defaults = {
      private_endpoint_ip_address = "10.42.1.2"
    }
  }
}

variables {
  region               = "ap-seoul-1"
  compartment_id       = "ocid1.compartment.oc1..networktestplaceholder"
  bastion_client_cidrs = ["203.0.113.10/32"]
}

run "default_network_boundaries" {
  command = apply

  assert {
    condition = (
      length(oci_core_subnet.network) == 2 &&
      oci_core_subnet.network["public"].cidr_block == "10.42.0.0/24" &&
      oci_core_subnet.network["private"].cidr_block == "10.42.1.0/24" &&
      oci_core_subnet.network["private"].prohibit_public_ip_on_vnic &&
      !oci_core_subnet.network["public"].prohibit_public_ip_on_vnic &&
      !oci_core_vcn.network.is_ipv6enabled
    )
    error_message = "Public/private IPv4 subnet boundaries must stay disjoint; private public IPs and IPv6 must remain disabled."
  }

  assert {
    condition = (
      length(oci_core_route_table.public.route_rules) == 1 &&
      length(oci_core_route_table.private.route_rules) == 1 &&
      one(oci_core_route_table.public.route_rules).destination == "0.0.0.0/0" &&
      one(oci_core_route_table.private.route_rules).destination == "0.0.0.0/0" &&
      one(oci_core_route_table.public.route_rules).network_entity_id == oci_core_internet_gateway.public.id &&
      one(oci_core_route_table.private.route_rules).network_entity_id == oci_core_nat_gateway.private.id &&
      oci_core_subnet.network["public"].route_table_id == oci_core_route_table.public.id &&
      oci_core_subnet.network["private"].route_table_id == oci_core_route_table.private.id &&
      !oci_core_nat_gateway.private.block_traffic
    )
    error_message = "Only the public subnet uses IGW; private default egress uses NAT, with no explicit internal NAT route."
  }

  assert {
    condition = (
      toset(oci_core_subnet.network["public"].security_list_ids) == toset([oci_core_security_list.public.id]) &&
      toset(oci_core_subnet.network["private"].security_list_ids) == toset([oci_core_security_list.private.id]) &&
      length(oci_core_security_list.public.ingress_security_rules) == 0 &&
      length(oci_core_security_list.public.egress_security_rules) == 0 &&
      length(oci_core_security_list.private.ingress_security_rules) == 0 &&
      length(oci_core_security_list.private.egress_security_rules) == 2 &&
      alltrue([for rule in oci_core_security_list.private.egress_security_rules :
        rule.protocol == "6" && !rule.stateless &&
        one(rule.tcp_options).min == 22 && one(rule.tcp_options).max == 22 &&
        contains(values(local.subnet_cidrs), rule.destination)
      ])
    )
    error_message = "Default security lists must not bypass NSGs. The only shared exception is bounded Bastion SSH egress."
  }

  assert {
    condition = (
      oci_core_network_security_group_security_rule.edge_https.network_security_group_id == oci_core_network_security_group.role["edge"].id &&
      oci_core_network_security_group_security_rule.edge_https.direction == "INGRESS" &&
      oci_core_network_security_group_security_rule.edge_https.source == "0.0.0.0/0" &&
      one(one(oci_core_network_security_group_security_rule.edge_https.tcp_options).destination_port_range).min == 443 &&
      one(one(oci_core_network_security_group_security_rule.edge_https.tcp_options).destination_port_range).max == 443 &&
      length(oci_core_network_security_group_security_rule.edge_http) == 0
    )
    error_message = "The default public transport entrypoint must be HTTPS on Edge only."
  }

  assert {
    condition = (
      oci_core_network_security_group_security_rule.application_from_edge.network_security_group_id == oci_core_network_security_group.role["application"].id &&
      oci_core_network_security_group_security_rule.application_from_edge.direction == "INGRESS" &&
      oci_core_network_security_group_security_rule.application_from_edge.source_type == "NETWORK_SECURITY_GROUP" &&
      oci_core_network_security_group_security_rule.application_from_edge.source == oci_core_network_security_group.role["edge"].id &&
      one(one(oci_core_network_security_group_security_rule.application_from_edge.tcp_options).destination_port_range).min == 8443 &&
      one(one(oci_core_network_security_group_security_rule.application_from_edge.tcp_options).destination_port_range).max == 8443 &&
      oci_core_network_security_group_security_rule.edge_to_application.destination_type == "NETWORK_SECURITY_GROUP" &&
      oci_core_network_security_group_security_rule.edge_to_application.destination == oci_core_network_security_group.role["application"].id &&
      one(one(oci_core_network_security_group_security_rule.edge_to_application.tcp_options).destination_port_range).min == 8443 &&
      one(one(oci_core_network_security_group_security_rule.edge_to_application.tcp_options).destination_port_range).max == 8443
    )
    error_message = "Edge-to-API must use the private NSG pair and the dedicated TLS port, not a public IP or raw Spring port."
  }

  assert {
    condition = (
      length(oci_core_network_security_group_security_rule.bastion_ssh) == 2 &&
      oci_bastion_bastion.management.max_session_ttl_in_seconds == 1800 &&
      toset(oci_bastion_bastion.management.client_cidr_block_allow_list) == toset(["203.0.113.10/32"]) &&
      oci_bastion_bastion.management.target_subnet_id == oci_core_subnet.network["private"].id &&
      oci_bastion_bastion.management.dns_proxy_status == "DISABLED" &&
      alltrue([for rule in oci_core_network_security_group_security_rule.bastion_ssh :
        rule.direction == "INGRESS" && rule.source_type == "CIDR_BLOCK" &&
        rule.source == "10.42.1.2/32" && rule.protocol == "6" && !rule.stateless &&
        one(one(rule.tcp_options).destination_port_range).min == 22 &&
        one(one(rule.tcp_options).destination_port_range).max == 22
      ])
    )
    error_message = "SSH must originate at the exact Bastion private endpoint with bounded sessions and client addresses."
  }

  assert {
    condition = (
      length(oci_core_network_security_group_security_rule.https_outbound) == 2 &&
      alltrue([for rule in oci_core_network_security_group_security_rule.https_outbound :
        rule.direction == "EGRESS" && rule.protocol == "6" && !rule.stateless &&
        rule.destination == "0.0.0.0/0" &&
        one(one(rule.tcp_options).destination_port_range).min == 443 &&
        one(one(rule.tcp_options).destination_port_range).max == 443
      ]) &&
      alltrue([for rule in oci_core_network_security_group_security_rule.path_mtu :
        rule.direction == "INGRESS" && rule.protocol == "1" &&
        one(rule.icmp_options).type == 3 && one(rule.icmp_options).code == 4
      ])
    )
    error_message = "Outbound internet access is HTTPS-only; IPv4 MTU support must not become general transport ingress."
  }

  assert {
    condition = (
      length(oci_core_network_security_group_security_rule.application_to_data) == 0 &&
      length(oci_core_network_security_group_security_rule.data_from_application) == 0 &&
      !contains(keys(oci_core_network_security_group_security_rule.bastion_ssh), "data") &&
      output.network_mapping.data_policy == "host_local" &&
      length(output.network_mapping.vnic_mapping) == 0
    )
    error_message = "Host-local data mode must not publish DB/Redis or imply an actual VNIC/VM mapping."
  }
}

run "separate_data_vnic_policy" {
  command = apply
  variables {
    data_access_mode = "private_vnic"
  }

  assert {
    condition = (
      length(oci_core_subnet.network) == 2 &&
      length(oci_core_network_security_group_security_rule.application_to_data) == 2 &&
      length(oci_core_network_security_group_security_rule.data_from_application) == 2 &&
      alltrue([for name, rule in oci_core_network_security_group_security_rule.data_from_application :
        rule.network_security_group_id == oci_core_network_security_group.role["data"].id &&
        rule.direction == "INGRESS" && rule.protocol == "6" && !rule.stateless &&
        rule.source_type == "NETWORK_SECURITY_GROUP" &&
        rule.source == oci_core_network_security_group.role["application"].id &&
        one(one(rule.tcp_options).destination_port_range).min == lookup({ mysql = 3306, redis = 6379 }, name) &&
        one(one(rule.tcp_options).destination_port_range).max == lookup({ mysql = 3306, redis = 6379 }, name)
      ]) &&
      alltrue([for name, rule in oci_core_network_security_group_security_rule.application_to_data :
        rule.network_security_group_id == oci_core_network_security_group.role["application"].id &&
        rule.direction == "EGRESS" && rule.protocol == "6" && !rule.stateless &&
        rule.destination_type == "NETWORK_SECURITY_GROUP" &&
        rule.destination == oci_core_network_security_group.role["data"].id &&
        one(one(rule.tcp_options).destination_port_range).min == lookup({ mysql = 3306, redis = 6379 }, name) &&
        one(one(rule.tcp_options).destination_port_range).max == lookup({ mysql = 3306, redis = 6379 }, name)
      ])
    )
    error_message = "A separate data VNIC accepts only Application NSG DB/Redis traffic; this must not add a third subnet or Edge access."
  }
}

run "http_is_explicit_opt_in" {
  command = apply
  variables {
    allow_http_redirect = true
  }

  assert {
    condition = (
      length(oci_core_network_security_group_security_rule.edge_http) == 1 &&
      oci_core_network_security_group_security_rule.edge_http[0].network_security_group_id == oci_core_network_security_group.role["edge"].id &&
      one(one(oci_core_network_security_group_security_rule.edge_http[0].tcp_options).destination_port_range).min == 80 &&
      one(one(oci_core_network_security_group_security_rule.edge_http[0].tcp_options).destination_port_range).max == 80
    )
    error_message = "Opt-in HTTP must stay on Edge port 80."
  }
}

run "nat_denial_keeps_internal_mapping" {
  command = apply
  variables {
    block_nat_traffic = true
  }

  assert {
    condition = (
      oci_core_nat_gateway.private.block_traffic &&
      one(oci_core_route_table.private.route_rules).network_entity_id == oci_core_nat_gateway.private.id &&
      oci_core_network_security_group_security_rule.edge_to_application.destination == oci_core_network_security_group.role["application"].id &&
      oci_core_network_security_group_security_rule.application_from_edge.source == oci_core_network_security_group.role["edge"].id
    )
    error_message = "The NAT denial switch must not replace the private Edge/API policy or route to IGW."
  }
}

run "alternate_network_environment" {
  command   = apply
  state_key = "alternate-network"
  variables {
    name_prefix    = "ccalab"
    vcn_cidr       = "192.168.64.0/24"
    subnet_newbits = 2
  }
  override_resource {
    target = oci_bastion_bastion.management
    values = {
      private_endpoint_ip_address = "192.168.64.66"
    }
  }
  assert {
    condition = (
      oci_core_subnet.network["public"].cidr_block == "192.168.64.0/26" &&
      oci_core_subnet.network["private"].cidr_block == "192.168.64.64/26" &&
      alltrue([for rule in oci_core_network_security_group_security_rule.bastion_ssh : rule.source == "192.168.64.66/32"]) &&
      length(output.network_mapping.vnic_mapping) == 0
    )
    error_message = "Environment-specific addressing must not require VM sizing or deployment changes."
  }
}

run "reject_open_management" {
  command = plan
  variables {
    bastion_client_cidrs = ["0.0.0.0/0"]
  }
  expect_failures = [var.bastion_client_cidrs]
}

run "reject_empty_management" {
  command = plan
  variables {
    bastion_client_cidrs = []
  }
  expect_failures = [var.bastion_client_cidrs]
}

run "reject_private_management_client" {
  command = plan
  variables {
    bastion_client_cidrs = ["10.42.1.10/32"]
  }
  expect_failures = [var.bastion_client_cidrs]
}

run "reject_public_vcn_range" {
  command = plan
  variables {
    vcn_cidr = "203.0.0.0/16"
  }
  expect_failures = [var.vcn_cidr]
}

run "reject_too_small_subnets" {
  command = plan
  variables {
    subnet_newbits = 14
  }
  expect_failures = [var.subnet_newbits]
}

run "reject_unknown_data_policy" {
  command = plan
  variables {
    data_access_mode = "public"
  }
  expect_failures = [var.data_access_mode]
}
