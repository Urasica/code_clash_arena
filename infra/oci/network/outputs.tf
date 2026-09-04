output "network_mapping" {
  description = "Provisioned network identifiers only. Empty VNIC mappings are NOT evidence of deployed isolation."
  value = {
    vcn_id          = oci_core_vcn.network.id
    subnet_ids      = { for role, subnet in oci_core_subnet.network : role => subnet.id }
    subnet_cidrs    = local.subnet_cidrs
    nsg_ids         = { for role, nsg in oci_core_network_security_group.role : role => nsg.id }
    route_table_ids = { public = oci_core_route_table.public.id, private = oci_core_route_table.private.id }
    gateway_ids     = { public = oci_core_internet_gateway.public.id, private = oci_core_nat_gateway.private.id }
    bastion_id      = oci_bastion_bastion.management.id
    bastion_ip      = oci_bastion_bastion.management.private_endpoint_ip_address
    api_tls_port    = 8443
    data_policy     = var.data_access_mode
    vnic_mapping    = {}
  }
}
