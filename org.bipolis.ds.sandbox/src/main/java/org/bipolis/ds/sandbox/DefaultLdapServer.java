package org.bipolis.ds.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.directory.api.ldap.codec.protocol.mina.LdapProtocolCodecActivator;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.store.LdifFileLoader;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.xdbm.Index;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(immediate = true)
public class DefaultLdapServer {

	@interface LdapServiceConfig {

		String instanceDirectory() default "ldapserver_work";

		int port() default 10389;

	}

	final Class<?> needToLoadCodecBeforeClass = LdapProtocolCodecActivator.class;

	private DirectoryService directoryService;

	@Reference
	private LdifProvider ldifProvider;

	private Path workDir;

	private LdapServer server;

	@Activate
	public void activate(LdapServiceConfig ldapServiceConfig) throws Exception {

		final Path ldifPath = Files.createTempFile("tempLdif", ".ldif");
		Files.write(ldifPath, ldifProvider.getLdif().array());

		workDir = Files.createTempDirectory(ldapServiceConfig.instanceDirectory());

		initDirectoryService();
		loadLdif(directoryService, ldifPath);

		server = new LdapServer();
		server.setTransports(new TcpTransport(ldapServiceConfig.port()));
		server.setDirectoryService(directoryService);
		server.start();

	}

	/**
	 * Add a new set of index on the given attributes
	 *
	 * @param partition The partition on which we want to add index
	 * @param attrs     The list of attributes to index
	 */
	private void addIndex(Partition partition, String... attrs) {
		// Index some attributes on the apache partition
		final Set<Index<?, String>> indexedAttributes = new HashSet<>();

		for (final String attribute : attrs) {
			indexedAttributes.add(new JdbmIndex<>(attribute, false));
		}

		((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
	}

	/**
	 * Add a new partition to the server
	 *
	 * @param partitionId The partition Id
	 * @param partitionDn The partition DN
	 * @param dnFactory   the DN factory
	 * @return The newly added partition
	 * @throws Exception If the partition can't be added
	 */
	private Partition addPartition(String partitionId, String partitionDn, DnFactory dnFactory)
			throws Exception {
		// Create a new partition with the given partition id
		final JdbmPartition partition = new JdbmPartition(directoryService.getSchemaManager(),
				dnFactory);
		partition.setId(partitionId);
		partition.setPartitionPath(
				new File(directoryService.getInstanceLayout().getPartitionsDirectory(), partitionId)
				.toURI());
		partition.setSuffixDn(new Dn(partitionDn));
		directoryService.addPartition(partition);

		return partition;
	}

	@Deactivate
	private void deActivate() {

		try {
			server.stop();
			Files.deleteIfExists(workDir);
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the server. It creates the partition, adds the index, and injects
	 * the context entries for the created partitions.
	 *
	 * @throws Exception if there were some problems while initializing the system
	 */
	private void initDirectoryService() throws Exception {
		// Initialize the LDAP service
		directoryService = new DefaultDirectoryService();

		directoryService.setInstanceLayout(new InstanceLayout(workDir.toFile()));

		final CacheService cacheService = new CacheService();
		cacheService.initialize(directoryService.getInstanceLayout());

		directoryService.setCacheService(cacheService);

		// first load the schema
		initSchemaPartition();

		// then the system partition
		// this is a MANDATORY partition
		// DO NOT add this via addPartition() method, trunk code complains about
		// duplicate partition
		// while initializing
		final JdbmPartition systemPartition = new JdbmPartition(directoryService.getSchemaManager(),
				directoryService.getDnFactory());
		systemPartition.setId("system");
		systemPartition
		.setPartitionPath(new File(directoryService.getInstanceLayout().getPartitionsDirectory(),
				systemPartition.getId()).toURI());
		systemPartition.setSuffixDn(new Dn(ServerDNConstants.SYSTEM_DN));
		systemPartition.setSchemaManager(directoryService.getSchemaManager());

		// mandatory to call this method to set the system partition
		// Note: this system partition might be removed from trunk
		directoryService.setSystemPartition(systemPartition);

		// Disable the ChangeLog system
		directoryService.getChangeLog().setEnabled(false);
		directoryService.setDenormalizeOpAttrsEnabled(true);

		// Now we can create as many partitions as we need
		// Create some new partitions named 'foo', 'bar' and 'apache'.

		final Partition barPartition = addPartition("bar", "dc=bar,dc=com",
				directoryService.getDnFactory());

		// Index some attributes on the apache partition
		addIndex(barPartition, "objectClass", "ou", "uid");

		// And start the service
		directoryService.startup();

		// Inject the context entry for dc=bar,dc=com partition
		try {
			directoryService.getAdminSession().lookup(barPartition.getSuffixDn());
		} catch (final LdapException lnnfe) {
			final Dn dnBar = new Dn("dc=bar,dc=com");
			final Entry entryBar = directoryService.newEntry(dnBar);
			entryBar.add("objectClass", "top", "domain", "extensibleObject");
			entryBar.add("dc", "bar");
			directoryService.getAdminSession().add(entryBar);
		}

		// We are all done !
	}

	/**
	 * initialize the schema manager and add the schema partition to diectory
	 * service
	 *
	 * @throws Exception if the schema LDIF files are not found on the classpath
	 */
	private void initSchemaPartition() throws Exception {
		final InstanceLayout instanceLayout = directoryService.getInstanceLayout();

		final File schemaPartitionDirectory = new File(instanceLayout.getPartitionsDirectory(),
				"schema");

		// Extract the schema on disk (a brand new one) and load the registries
		if (schemaPartitionDirectory.exists()) {
			System.out.println("schema partition already exists, skipping schema extraction");
		} else {
			final SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(
					instanceLayout.getPartitionsDirectory());
			extractor.extractOrCopy();
		}

		final SchemaLoader loader = new LdifSchemaLoader(schemaPartitionDirectory);
		final SchemaManager schemaManager = new DefaultSchemaManager(loader);

		// We have to load the schema now, otherwise we won't be able
		// to initialize the Partitions, as we won't be able to parse
		// and normalize their suffix Dn
		schemaManager.loadAllEnabled();

		final List<Throwable> errors = schemaManager.getErrors();

		if (errors.size() != 0) {
			throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
		}

		directoryService.setSchemaManager(schemaManager);

		// Init the LdifPartition with schema
		final LdifPartition schemaLdifPartition = new LdifPartition(schemaManager,
				directoryService.getDnFactory());
		schemaLdifPartition.setPartitionPath(schemaPartitionDirectory.toURI());

		// The schema partition
		final SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
		schemaPartition.setWrappedPartition(schemaLdifPartition);
		directoryService.setSchemaPartition(schemaPartition);
	}

	/**
	 * Load ldif.
	 *
	 * @param service  the service
	 * @param ldifFile the ldif file
	 * @throws Exception the exception
	 */
	private void loadLdif(DirectoryService service, Path ldifFile) throws Exception {

		// We are done !
		//

		final LdifFileLoader ldifFileLoader = new LdifFileLoader(service.getAdminSession(),
				ldifFile.toFile(), null);
		final int ldfiResult = ldifFileLoader.execute();

		System.out.println("------------->" + ldfiResult);
	}

}