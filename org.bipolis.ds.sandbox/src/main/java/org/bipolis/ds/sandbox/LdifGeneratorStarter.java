package org.bipolis.ds.sandbox;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

public class LdifGeneratorStarter {

  public static void go() throws Exception {

    final int size = 10;
    final LdifGenerator ldiffGenerator = new LdifGenerator();
    final File file = new File("/tmp/test.ldif");

    if (!file.exists()) {
      file.createNewFile();
    }

    final FileWriter fw = new FileWriter(file);
    final BufferedWriter bw = new BufferedWriter(fw);

    // configure following values

    final int TOTAL_USER_COUNT = size * size * size * size;
    final int TOP_GROUPS_COUNT = size;
    final int SUB_GROUPS_COUNT = size;
    final int CHILD_GROUPS_COUNT = size;
    final int USERS_PER_GROUP = size;

    // Names
    final String DOMAIN_TL = "com";
    final String DOMAIN_DOMAIN = "bar";
    final String ORGANIZATION = "MyOrga";
    final String ORGANIZATION_UNIT = "MyOrgaUnit";

    // Organizational Roles names
    final String USERS = "users";
    final String GROUPS = "groups";
    final String ADMINS = "admins";

    // Admin Details
    final String ADMIN_NAME = "wso2admin@wso2.com";
    final String ADMIN_PASSWORD = "admin=";
    final String ADMIN_ROLE = "wso2admins";

    final String USER_NAME_PREFIX = "user";
    final String USER_EMAIl_DOMAIN = "@wso2.com";
    final String USER_PASSWORD = "password=";

    final String GROUP_PARENT_NAME = "parentGroup";
    final String GROUP_SUB__NAME = "subGroup";
    final String GROUP_CHILD__NAME = "childGroup";

    bw.write("version: 1\n\n");

    // creating county
    bw.append(ldiffGenerator.generateDomainComponent(DOMAIN_TL, DOMAIN_DOMAIN));

    // creating origination DN
    bw.append(
        ldiffGenerator.generateOrganization(ORGANIZATION, ldiffGenerator.getCurrentCountryDN()));

    // creating originationUnit DN
    bw.append(ldiffGenerator.generateOrganizationalUnit(ORGANIZATION_UNIT,
        ldiffGenerator.getCurrentOrganizationDN()));

    // creating ADMIN organizational Role
    bw.append(ldiffGenerator.generateOrganizationalRole(ADMINS,
        ldiffGenerator.getCurrentOrganizationalUnitDN()));

    // adding ADMIN USER
    bw.append(ldiffGenerator.generateUser(LdifGenerator.CN, ADMIN_NAME, ADMIN_PASSWORD, ADMIN_NAME,
        ldiffGenerator.getCurrentOrganizationalRoleDN()));

    bw.append(ldiffGenerator.generateGroup(LdifGenerator.CN, ADMIN_ROLE,
        ldiffGenerator.getCurrentOrganizationalRoleDN()));

    bw.append(ldiffGenerator.generateMember(LdifGenerator.MEMBER, LdifGenerator.CN, ADMIN_NAME,
        ldiffGenerator.getCurrentOrganizationalRoleDN()));
    bw.append(LdifGenerator.NL);

    // creating Users organizational Role
    bw.append(ldiffGenerator.generateOrganizationalRole(USERS,
        ldiffGenerator.getCurrentOrganizationalUnitDN()));

    final String userPath = ldiffGenerator.getCurrentOrganizationalRoleDN();
    // creating users OrgRole
    for (int i = 1; i <= TOTAL_USER_COUNT; i++) {
      bw.append(
          ldiffGenerator.generateUser(LdifGenerator.CN, USER_NAME_PREFIX + i + USER_EMAIl_DOMAIN,
              USER_PASSWORD, USER_NAME_PREFIX + i + USER_EMAIl_DOMAIN,
              ldiffGenerator.getCurrentOrganizationalRoleDN()));
    }

    System.out.println("Generating users completed !");

    // creating groups OrgRole
    bw.append(ldiffGenerator.generateOrganizationalRole(GROUPS,
        ldiffGenerator.getCurrentOrganizationalUnitDN()));

    final String groupsOrgRoleDN = ldiffGenerator.getCurrentOrganizationalRoleDN();

    for (int i = 1; i <= TOP_GROUPS_COUNT; i++) {

      bw.append(ldiffGenerator.generateOrganizationalRole(GROUP_PARENT_NAME + i, groupsOrgRoleDN));
      final String currentTopGroupDN = ldiffGenerator.getCurrentOrganizationalRoleDN();

      for (int j = 1; j <= SUB_GROUPS_COUNT; j++) {

        final String subGroupID = "_" + i + "_" + j;
        bw.append(ldiffGenerator.generateOrganizationalRole(GROUP_SUB__NAME + subGroupID,
            currentTopGroupDN));
        final String currentSubGroupDN = ldiffGenerator.getCurrentOrganizationalRoleDN();

        for (int k = 1; k <= CHILD_GROUPS_COUNT; k++) {
          final String childGroupID = subGroupID + "_" + k;
          bw.append(ldiffGenerator.generateGroup(LdifGenerator.CN, GROUP_CHILD__NAME + childGroupID,
              currentSubGroupDN));

          // Adding users to above group.
          for (int l = 0; l < USERS_PER_GROUP; l++) {

            // Adding 1 to avoid getting zero and floor to avoid getting number more than
            // TOTAL_USER_COUNT
            final int userID = (int) Math.floor(Math.random() * TOTAL_USER_COUNT) + 1;
            bw.append(ldiffGenerator.generateMember(LdifGenerator.MEMBER, LdifGenerator.CN,
                USER_NAME_PREFIX + userID + USER_EMAIl_DOMAIN, userPath));
          }
          bw.append(LdifGenerator.NL);
        }
      }
    }

    System.out.println("Generating groups completed !");

    bw.close();
    System.out.println("Done !");
  }

  /**
   * Example LDIF Generator
   *
   * @throws Exception
   *
   */

  public static void main(String[] args) throws Exception {
    LdifGeneratorStarter.go();
  }

}