/**
 *
 * (c) 2003-2012 MuleSoft, Inc. This software is protected under international
 * copyright law. All use of this software is subject to MuleSoft's Master
 * Subscription Agreement (or other Terms of Service) separately entered
 * into between you and MuleSoft. If such an agreement is not in
 * place, you may not use the software.
 */

package org.mule.module.hubspot.integration;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mule.api.ConnectionException;
import org.mule.module.hubspot.HubSpotConnector;
import org.mule.module.hubspot.client.HubSpotClientsManager;
import org.mule.module.hubspot.exception.HubSpotConnectorAccessTokenExpiredException;
import org.mule.module.hubspot.exception.HubSpotConnectorException;
import org.mule.module.hubspot.exception.HubSpotConnectorNoAccessTokenException;
import org.mule.module.hubspot.model.contact.Contact;
import org.mule.module.hubspot.model.contact.ContactDeleted;
import org.mule.module.hubspot.model.contact.ContactList;
import org.mule.module.hubspot.model.contact.ContactProperties;
import org.mule.module.hubspot.model.contact.ContactPropertiesLifecycleStage;
import org.mule.module.hubspot.model.contact.ContactPropertiesNumberOfEmployees;
import org.mule.module.hubspot.model.contact.ContactQuery;
import org.mule.module.hubspot.model.contact.ContactStatistics;
import org.mule.module.hubspot.model.contactproperty.CustomContactProperty;
import org.mule.module.hubspot.model.contactproperty.CustomContactPropertyFieldType;
import org.mule.module.hubspot.model.contactproperty.CustomContactPropertyGroup;
import org.mule.module.hubspot.model.contactproperty.CustomContactPropertyType;
import org.mule.module.hubspot.model.email.EmailSubscription;
import org.mule.module.hubspot.model.list.HubSpotList;
import org.mule.module.hubspot.model.list.HubSpotListAddContactToListResponse;
import org.mule.module.hubspot.model.list.HubSpotListFilter;
import org.mule.module.hubspot.model.list.HubSpotListFilters;
import org.mule.module.hubspot.model.list.HubSpotListLists;
import org.mule.module.hubspot.model.list.HubSpotNewList;
import org.mule.util.store.SimpleMemoryObjectStore;

public class HubSpotConnectorIT {

	static final private String USER_ID = "1";

	private HubSpotConnector connector;

	@Before
	public void setUp() throws IOException, ConnectionException,
			HubSpotConnectorException, HubSpotConnectorNoAccessTokenException {
		// Load the .properties
		Properties prop = new Properties();
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		InputStream stream = loader
				.getResourceAsStream("hubspotconnectorit.properties");
		prop.load(stream);

		// Save the props in the class attributes
		String authResult = prop.getProperty("hubspot.it.authresult");
		
		connector = new HubSpotConnector();
		connector.setClientId(prop.getProperty("hubspot.it.clientid"));
		connector.setHubId(prop.getProperty("hubspot.it.hubid"));
		connector.setScope(prop.getProperty("hubspot.it.scope"));
		connector.setCallbackUrl(prop.getProperty("hubspot.it.callbackurl"));
		connector.setObjectStore(new SimpleMemoryObjectStore<Serializable>());
		connector.initialize();
		try {
			connector.authenticate(USER_ID, null, null, null, null, null);
		} catch (Throwable e) {} // Expected exception because we are not passing a map for the headers. Only intention is to initialize the client
		
		if (StringUtils.isEmpty(authResult)) {
			
			Map<String, Object> m = new HashMap<String, Object>();
			String url = connector.authenticate(USER_ID, null, null, null, null, m);
			
			throw new RuntimeException("Call this url and gather the URL response as the authresult: " + url);
		} else {
			connector.authenticateResponse(authResult);
		}
	}
	
	
	
	
	/**
	 * 1. create a new Contact (OP: createContact)
	 * 2. retrieve contact by email (OP: getContactByEmail)
	 * 3. update the contact (OP: updateContact)
	 * 4. retrieve contact by id (OP: getContactById)
	 * 5. delete contact by id (OP: 
	 */
	@Test
	public void createRetrieveDeleteContact() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		
		// 1. Create a new contact
		ContactProperties cp;
		
		String email = createNewContact();
		
		// 2. Retrieve the contact by email and check that all the properties setted are stablished
		Contact c = connector.getContactByEmail(USER_ID, email);
		Assert.assertNotNull(c);
		
		cp = c.getContactProperties();
		Assert.assertNotNull(cp);		
		Assert.assertFalse(StringUtils.isEmpty(c.getVid()));
		Assert.assertEquals(cp.getFirstname(), "theFirstName");
		Assert.assertEquals(cp.getLastname(), "theLastName");
		Assert.assertEquals(cp.getNumemployees(), ContactPropertiesNumberOfEmployees._25_50);
		Assert.assertEquals(cp.getLifecyclestage(), ContactPropertiesLifecycleStage.LEAD);
		Assert.assertEquals(cp.getCity(), "beautifulCity");
		
		// 3. Update the lastname of the contact
		cp = new ContactProperties();
		cp.setLastname("lastNameModified");
		
		connector.updateContact(USER_ID, c.getVid(), cp);
		
		// 4. Retrieve again the same contact but this time by ID, and check that the lastname holds the modified value
		c = connector.getContactById(USER_ID, c.getVid());
		
		Assert.assertNotNull(c);
		
		cp = c.getContactProperties();
		
		Assert.assertNotNull(cp);
		Assert.assertEquals(cp.getLastname(), "lastNameModified");
		
		// 5. Delete the contact by his ID and check the response
		ContactDeleted cd = connector.deleteContact(USER_ID, c.getVid());
		
		Assert.assertNotNull(cd);
		Assert.assertTrue(cd.getDeleted());
		Assert.assertEquals(cd.getVid(), c.getVid());
	}
	
	
	@Test
	public void getContacts() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		
		createNewContact();
		
		ContactList cl = connector.getAllContacts(USER_ID, null, null);
		
		Assert.assertNotNull(cl);
		Assert.assertTrue(cl.getContacts().size() > 0);
		Assert.assertFalse(StringUtils.isEmpty(cl.getContacts().get(0).getContactProperties().getFirstname()));
		
		cl = connector.getRecentContacts(USER_ID, null, null, null);
		
		Assert.assertNotNull(cl);
		Assert.assertTrue(cl.getContacts().size() > 0);
		Assert.assertFalse(StringUtils.isEmpty(cl.getContacts().get(0).getContactProperties().getFirstname()));
		
		String q = "mule";
		ContactQuery cq = connector.getContactsByQuery(USER_ID, q, null);
		
		Assert.assertNotNull(cq);
		Assert.assertEquals(cq.getQuery(), q);
		Assert.assertTrue(cq.getContacts().size() > 0);
		Assert.assertFalse(StringUtils.isEmpty(cq.getContacts().get(0).getContactProperties().getFirstname()));
	}
	
	@Test
	public void getStatistics() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		ContactStatistics cs = connector.getContactStatistics(USER_ID);
		
		Assert.assertNotNull(cs);
		Assert.assertTrue(cs.getContacts() >= 0l);
		Assert.assertTrue(cs.getLastNewContactAt() >= 0l);
	}
	
	@Test
	public void getLists() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		HubSpotListLists hsll = connector.getContactsLists(USER_ID, null, null);
		
		Assert.assertNotNull(hsll);
		Assert.assertEquals(hsll.getLists().size(), 3);
		
		HubSpotList hsl = connector.getContactListById(USER_ID, "1");		
		
		Assert.assertNotNull(hsl);
		Assert.assertEquals(hsl.getPortalId(), "237093");
		
		hsll = connector.getDynamicContactLists(USER_ID, null, null);
		
		Assert.assertNotNull(hsll);
		Assert.assertEquals(hsll.getLists().size(), 1);
		
		ContactList cl = connector.getContactsInAList(USER_ID, "1", null, null, null);
		
		Assert.assertNotNull(cl);
		Assert.assertTrue(cl.getContacts().size() > 0);		
	}
	
	@Test
	public void getEmailSubscriptions() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		EmailSubscription es = connector.getEmailSubscriptions(USER_ID, null);
		
		Assert.assertNotNull(es);
		Assert.assertNotNull(es.getSubscriptionDefinitions());
		Assert.assertTrue(es.getSubscriptionDefinitions().size() > 0);
	}
	
	@Test
	public void getContactProperties() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		List<CustomContactProperty> lccp = connector.getAllCustomProperties(USER_ID);
		
		Assert.assertNotNull(lccp);
		Assert.assertTrue(lccp.size() > 0);
		
		long date = (new Date()).getTime();
		final String GROUP_PREPOSITION = "ccpg_";
		final String PROP_PREPOSITION = "ccp_";
		
		// Create Custom Property Group
		CustomContactPropertyGroup ccpg = new CustomContactPropertyGroup();
		ccpg.setName(GROUP_PREPOSITION + date);
		ccpg.setDisplayOrder(5);
		ccpg.setDisplayName("Im the group!");
				
		ccpg = connector.createCustomPropertyGroup(USER_ID, ccpg);
		
		Assert.assertNotNull(ccpg);
		Assert.assertEquals(GROUP_PREPOSITION + date, ccpg.getName());		
		
		// Create Custom Property
		CustomContactProperty ccp = new CustomContactProperty();
		
		ccp.setName(PROP_PREPOSITION + date);
		ccp.setGroupName(GROUP_PREPOSITION + date);
		ccp.setFieldType(CustomContactPropertyFieldType.TEXT);
		ccp.setType(CustomContactPropertyType.STRING);
		ccp.setLabel("Im the label");
		ccp.setFormField(true);
		
		ccp = connector.createCustomProperty(USER_ID, ccp);
				
		Assert.assertNotNull(ccp);
		Assert.assertEquals(PROP_PREPOSITION + date, ccp.getName());
		Assert.assertEquals(GROUP_PREPOSITION + date, ccp.getGroupName());
		
		// Update the property		
		ccp = new CustomContactProperty();
		ccp.setLabel("Im the new label");
		
		ccp = connector.updateCustomProperty(USER_ID, PROP_PREPOSITION + date, ccp);
		
		Assert.assertNotNull(ccp);
		Assert.assertEquals("Im the new label", ccp.getLabel());
		
		// Delete the property
		connector.deleteCustomProperty(USER_ID, ccp.getName());
		
		// Update Property Group
		ccpg = new CustomContactPropertyGroup();
		ccpg.setDisplayName("Im the new group name");
				
		connector.updateCustomPropertyGroup(USER_ID, GROUP_PREPOSITION + date, ccpg);
		
		// Retrieve the Property Group
		ccpg = connector.getCustomPropertyGroup(USER_ID, GROUP_PREPOSITION + date);
		
		Assert.assertNotNull(ccpg);
		Assert.assertEquals("Im the new group name", ccpg.getDisplayName());
		
		connector.deleteCustomPropertyGroup(USER_ID, ccpg.getName());
	}
	
	/*
	 * 
	 * A shutdown application consist in persist the credentialsManager data but erase the clientsManager data
	 */
	@Test
	public void simulateShutdownApplication() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		connector.setClientsManager(new HubSpotClientsManager());		
		getContacts();
	}
	
	@Test
	public void addAndRemoveContactFromLists() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		// Create a new contact
		String email = createNewContact();
		
		// Retrieve the contact by email and check that all the properties setted are stablished
		Contact c = connector.getContactByEmail(USER_ID, email);
		Assert.assertNotNull(c);
		
		// Retrieve all the email lists and use one that is not dynamic
		HubSpotListLists hsll = connector.getContactsLists(USER_ID, null, null);
		Assert.assertNotNull(hsll);
		
		for (HubSpotList hbl : hsll.getLists()) {
			// It must be a non dynamic list
			if (!hbl.getDynamic()) {
				String listId = hbl.getListId();
				
				HubSpotListAddContactToListResponse hslactlr = connector.addExistingContactInAList(USER_ID, listId, c.getVid());
				
				Assert.assertNotNull(hslactlr);
				Assert.assertNotNull(hslactlr.getUpdated());
				Assert.assertEquals(1, hslactlr.getUpdated().size());
				Assert.assertEquals(Integer.valueOf(c.getVid()), hslactlr.getUpdated().get(0));
				
				break;
			}
		}
			
		
		// Delete the contact by his ID and check the response
		ContactDeleted cd = connector.deleteContact(USER_ID, c.getVid());
		
		Assert.assertNotNull(cd);
		Assert.assertTrue(cd.getDeleted());
		Assert.assertEquals(cd.getVid(), c.getVid());		
	}
	
	@Test
	public void createContactList() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		HubSpotNewList hsnl = new HubSpotNewList();
		long date = (new Date()).getTime();
		hsnl.setName("test list " + date);
		hsnl.setDynamic(true);
		hsnl.setPortalId(connector.getHubId());
		
		HubSpotListFilter hblf = new HubSpotListFilter();
		hblf.setOperator("EQ");
		hblf.setValue("@hubspot");
		hblf.setProperty("twitterhandle");
		hblf.setType("string");
		
		List<HubSpotListFilter> lhblf = new LinkedList<HubSpotListFilter>();
		lhblf.add(hblf);
		
		HubSpotListFilters hbf = new HubSpotListFilters();
		hbf.setFilters(lhblf);
		
		List<HubSpotListFilters> lhblfs = new LinkedList<HubSpotListFilters>();
		lhblfs.add(hbf);
		
		HubSpotList hsl = connector.createContactList(USER_ID, hsnl, lhblfs);
		
		Assert.assertNotNull(hsl);
	}
		
	private String createNewContact() throws HubSpotConnectorException, HubSpotConnectorNoAccessTokenException, HubSpotConnectorAccessTokenExpiredException {
		ContactProperties cp = new ContactProperties();
		long date = (new Date()).getTime();
		String email = String.format("%d@mulesoft.com", date);
		
		cp.setEmail(email);
		cp.setFirstname("theFirstName");
		cp.setLastname("theLastName");
		cp.setNumemployees(ContactPropertiesNumberOfEmployees._25_50);
		cp.setLifecyclestage(ContactPropertiesLifecycleStage.LEAD);
		cp.setCity("beautifulCity");
		
		Contact c = connector.createContact(USER_ID, cp);
		c.toString();
		
		return email;
	}
}