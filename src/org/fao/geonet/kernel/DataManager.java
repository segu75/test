//==============================================================================
//===
//=== DataManager
//===
//=============================================================================
//===	Copyright (C) 2001-2007 Food and Agriculture Organization of the
//===	United Nations (FAO-UN), United Nations World Food Programme (WFP)
//===	and United Nations Environment Programme (UNEP)
//===
//===	This program is free software; you can redistribute it and/or modify
//===	it under the terms of the GNU General Public License as published by
//===	the Free Software Foundation; either version 2 of the License, or (at
//===	your option) any later version.
//===
//===	This program is distributed in the hope that it will be useful, but
//===	WITHOUT ANY WARRANTY; without even the implied warranty of
//===	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
//===	General Public License for more details.
//===
//===	You should have received a copy of the GNU General Public License
//===	along with this program; if not, write to the Free Software
//===	Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA
//===
//===	Contact: Jeroen Ticheler - FAO - Viale delle Terme di Caracalla 2,
//===	Rome - Italy. email: geonetwork@osgeo.org
//==============================================================================

package org.fao.geonet.kernel;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Vector;
import jeeves.constants.Jeeves;
import jeeves.resources.dbms.Dbms;
import jeeves.server.context.ServiceContext;
import jeeves.utils.Log;
import jeeves.utils.SerialFactory;
import jeeves.utils.Xml;
import org.fao.geonet.constants.Edit;
import org.fao.geonet.constants.Geonet;
import org.fao.geonet.kernel.schema.MetadataSchema;
import org.fao.geonet.kernel.search.SearchManager;
import org.fao.geonet.kernel.setting.SettingManager;
import org.fao.geonet.util.ISODate;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.Text;

//=============================================================================

/** Handles all operations on metadata (select,insert,update,delete etc...)
  */

public class DataManager
{
	//--------------------------------------------------------------------------
	//---
	//--- Constructor
	//---
	//--------------------------------------------------------------------------

	/** initializes the search manager and index not-indexed metadata
	  */

	public DataManager(SearchManager sm, AccessManager am, Dbms dbms, SettingManager ss,
							 String baseURL) throws Exception
	{
		searchMan = sm;
		accessMan = am;
		settingMan= ss;

		this.baseURL = baseURL;

		// get all metadata from DB
		Element result = dbms.select("SELECT id, changeDate FROM Metadata ORDER BY id ASC");
		List list = result.getChildren();

		// System.out.println("DB CONTENT:\n" + Xml.getString(result)); // DEBUG

		// get all metadata from index
		Hashtable docs = searchMan.getDocs();

		// System.out.println("INDEX CONTENT:"); // DEBUG

		// index all metadata in DBMS if needed
		for(int i = 0; i < list.size(); i++)
		{
			// get metadata
			Element record = (Element) list.get(i);
			String  id     = record.getChildText("id");

			// System.out.println("- record (" + id + ")"); // DEBUG

			Hashtable idxRec = (Hashtable)docs.get(id);

			// if metadata is not indexed index it
			if (idxRec == null)
				indexMetadata(dbms, id);

			// else, if indexed version is not the latest index it
			else
			{
				docs.remove(id);

				String lastChange = record.getChildText("changedate");
				String idxLastChange = (String)idxRec.get("_changeDate");

				// System.out.println("  - lastChange: " + lastChange); // DEBUG
				// System.out.println("  - idxLastChange: " + idxLastChange); // DEBUG

				if (!idxLastChange.equalsIgnoreCase(lastChange)) // date in index contains 't', date in DBMS contains 'T'
					indexMetadata(dbms, id);
			}
		}
		// System.out.println("INDEX SURPLUS:"); // DEBUG

		// remove from index metadata not in DBMS
		for (Enumeration i = docs.keys(); i.hasMoreElements(); )
		{
			String id = (String)i.nextElement();
			searchMan.delete("_id", id);

			// System.out.println("- record (" + id + ")"); // DEBUG
		}
	}

	//--------------------------------------------------------------------------

	public void indexMetadata(Dbms dbms, String id) throws Exception
	{
		indexMetadata(dbms, id, searchMan);
	}

	//--------------------------------------------------------------------------

	public static void indexMetadata(Dbms dbms, String id, SearchManager sm) throws Exception
	{
		try
		{
			indexMetadataI(dbms, id, sm);
		}
		catch (JDOMException e)
		{
			Log.error(Geonet.DATA_MANAGER, "The metadata with id="+id+" is corrupted");
		}
	}

	//--------------------------------------------------------------------------

	private static void indexMetadataI(Dbms dbms, String id, SearchManager sm) throws Exception
	{
		Vector moreFields = new Vector();

		// get metadata table fields
		Element md   = XmlSerializer.select(dbms, "Metadata", id);
		String  root = md.getName();

		String query ="SELECT schemaId, createDate, changeDate, source, isTemplate, title, uuid, "+
									"isHarvested, owner, groupOwner FROM Metadata WHERE id = " + id;

		Element rec = dbms.select(query).getChild("record");

		String  schema     = rec.getChildText("schemaid");
		String  createDate = rec.getChildText("createdate");
		String  changeDate = rec.getChildText("changedate");
		String  source     = rec.getChildText("source");
		String  isTemplate = rec.getChildText("istemplate");
		String  title      = rec.getChildText("title");
		String  uuid       = rec.getChildText("uuid");
		String  isHarvested= rec.getChildText("isharvested");
		String  owner      = rec.getChildText("owner");
		String  groupOwner = rec.getChildText("groupowner");

		moreFields.add(makeField("_root",        root,        true, true, false));
		moreFields.add(makeField("_schema",      schema,      true, true, false));
		moreFields.add(makeField("_createDate",  createDate,  true, true, false));
		moreFields.add(makeField("_changeDate",  changeDate,  true, true, false));
		moreFields.add(makeField("_source",      source,      true, true, false));
		moreFields.add(makeField("_isTemplate",  isTemplate,  true, true, false));
		moreFields.add(makeField("_title",       title,       true, true, false));
		moreFields.add(makeField("_uuid",        uuid,        true, true, false));
		moreFields.add(makeField("_isHarvested", isHarvested, true, true, false));
		moreFields.add(makeField("_owner",       owner,       true, true, false));
		moreFields.add(makeField("_dummy",       "0",        false, true, false));

		if (groupOwner != null)
			moreFields.add(makeField("_groupOwner", groupOwner, true, true, false));

		// get privileges
		List operations = dbms.select("SELECT groupId, operationId FROM OperationAllowed "+
												"WHERE metadataId = " + id + " ORDER BY operationId ASC").getChildren();

		for (Iterator iter = operations.iterator(); iter.hasNext(); )
		{
			Element operation   = (Element)iter.next();
			String  groupId     = operation.getChildText("groupid");
			String  operationId = operation.getChildText("operationid");

			moreFields.add(makeField("_op" + operationId, groupId, true, true, false));
		}
		// get categories
		List categories = dbms.select("SELECT id, name FROM MetadataCateg, Categories "+
												"WHERE metadataId = " + id + " AND categoryId = id ORDER BY id").getChildren();

		for (Iterator iter = categories.iterator(); iter.hasNext(); )
		{
			Element category     = (Element)iter.next();
			String  categoryName = category.getChildText("name");

			moreFields.add(makeField("_cat", categoryName, true, true, false));
		}

		sm.index(schema, md, id, moreFields, isTemplate, title);
	}

	//--------------------------------------------------------------------------

	private static Element makeField(String name, String value, boolean store,
												boolean index, boolean token)
	{
		Element field = new Element("Field");

		field.setAttribute("name",   name);
		field.setAttribute("string", value);
		field.setAttribute("store",  store+"");
		field.setAttribute("index",  index+"");
		field.setAttribute("token",  token+"");

		return field;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Schema management API
	//---
	//--------------------------------------------------------------------------

	public void addSchema(String id, String xmlSchemaFile, String xmlSuggestFile) throws Exception
	{
		editLib.addSchema(id, xmlSchemaFile, xmlSuggestFile);
	}

	//--------------------------------------------------------------------------

	public MetadataSchema getSchema(String name)
	{
		return editLib.getSchema(name);
	}

	//--------------------------------------------------------------------------

	public Set<String> getSchemas()
	{
		return editLib.getSchemas();
	}

	//--------------------------------------------------------------------------

	public boolean existsSchema(String name)
	{
		return editLib.existsSchema(name);
	}

	//--------------------------------------------------------------------------

	public String getSchemaDir(String name)
	{
		return editLib.getSchemaDir(name);
	}

	//--------------------------------------------------------------------------

	public void validate(String schema, Element md) throws Exception
	{
		Xml.validate(editLib.getSchemaDir(schema) + Geonet.File.SCHEMA, md);
	}

	//--------------------------------------------------------------------------

	public AccessManager getAccessManager()
	{
		return accessMan;
	}

	//--------------------------------------------------------------------------
	//---
	//--- General purpose API
	//---
	//--------------------------------------------------------------------------

	public String extractUUID(String schema, Element md) throws Exception
	{
		String styleSheet = editLib.getSchemaDir(schema) + Geonet.File.EXTRACT_UUID;
		String uuid       = Xml.transform(md, styleSheet).getText().trim();

		Log.debug(Geonet.DATA_MANAGER, "Extracted UUID '"+ uuid +"' for schema '"+ schema +"'");

		//--- needed to detach md from the document
		md.detach();

		return uuid;
	}

	//--------------------------------------------------------------------------

	public Element setUUID(String schema, String uuid, Element md) throws Exception
	{
		//--- setup environment

		Element env = new Element("env");
		env.addContent(new Element("uuid").setText(uuid));

		//--- setup root element

		Element root = new Element("root");
		root.addContent(md);
		root.addContent(env);

		//--- do an XSL  transformation

		String styleSheet = editLib.getSchemaDir(schema) + Geonet.File.SET_UUID;

		return Xml.transform(root, styleSheet);
	}

	//--------------------------------------------------------------------------

	public String getMetadataId(Dbms dbms, String uuid) throws Exception
	{
		String query = "SELECT id FROM Metadata WHERE uuid=?";

		List list = dbms.select(query, uuid).getChildren();

		if (list.size() == 0)
			return null;

		Element record = (Element) list.get(0);

		return record.getChildText("id");
	}

	//--------------------------------------------------------------------------

	public String getMetadataUuid(Dbms dbms, String id) throws Exception
	{
		String query = "SELECT uuid FROM Metadata WHERE id=?";

		List list = dbms.select(query, new Integer(id)).getChildren();

		if (list.size() == 0)
			return null;

		Element record = (Element) list.get(0);

		return record.getChildText("uuid");
	}

	//--------------------------------------------------------------------------

	public MdInfo getMetadataInfo(Dbms dbms, String id) throws Exception
	{
		String query = "SELECT id, uuid, schemaId, isTemplate, isHarvested, createDate, "+
							"       changeDate, source, title, root, owner, groupOwner "+
							"FROM   Metadata "+
							"WHERE id=?";

		List list = dbms.select(query, new Integer(id)).getChildren();

		if (list.size() == 0)
			return null;

		Element record = (Element) list.get(0);

		MdInfo info = new MdInfo();

		info.id          = id;
		info.uuid        = record.getChildText("uuid");
		info.schemaId    = record.getChildText("schemaid");
		info.isHarvested = "y".equals(record.getChildText("isharvested"));
		info.createDate  = record.getChildText("createdate");
		info.changeDate  = record.getChildText("changedate");
		info.source      = record.getChildText("source");
		info.title       = record.getChildText("title");
		info.root        = record.getChildText("root");
		info.owner       = record.getChildText("owner");
		info.groupOwner  = record.getChildText("groupowner");

		String temp = record.getChildText("istemplate");

		if ("y".equals(temp))
			info.template = MdInfo.Template.TEMPLATE;

		else if ("s".equals(temp))
			info.template = MdInfo.Template.SUBTEMPLATE;

		else
			info.template = MdInfo.Template.METADATA;

		return info;
	}

	//--------------------------------------------------------------------------

	public String getVersion(String id)
	{
		return editLib.getVersion(id);
	}

	//--------------------------------------------------------------------------

	public String getNewVersion(String id)
	{
		return editLib.getNewVersion(id);
	}

	//--------------------------------------------------------------------------

	public void setTemplate(Dbms dbms, int id, String isTemplate, String title) throws Exception
	{
		if (title == null) dbms.execute("UPDATE Metadata SET isTemplate=? WHERE id=?", isTemplate, id);
		else               dbms.execute("UPDATE Metadata SET isTemplate=?, title=? WHERE id=?", isTemplate, title, id);
		indexMetadata(dbms, Integer.toString(id));
	}

	//--------------------------------------------------------------------------

	public void setHarvested(Dbms dbms, int id, String harvestUuid) throws Exception
	{
		String value = (harvestUuid != null) ? "y" : "n";
		String query = "UPDATE Metadata SET isHarvested=?, harvestUuid=? WHERE id=?";

		dbms.execute(query, value, harvestUuid, id);
		indexMetadata(dbms, Integer.toString(id));
	}

	//--------------------------------------------------------------------------

	public void setHarvested(Dbms dbms, int id, String harvestUuid, String harvestUri) throws Exception
	{
		String value = (harvestUuid != null) ? "y" : "n";
		String query = "UPDATE Metadata SET isHarvested=?, harvestUuid=?, harvestUri=? WHERE id=?";

		dbms.execute(query, value, harvestUuid, harvestUri, id);
		indexMetadata(dbms, Integer.toString(id));
	}

	//---------------------------------------------------------------------------

	public String getSiteURL()
	{
		String host    = settingMan.getValue("system/server/host");
		String port    = settingMan.getValue("system/server/port");
		String locServ = baseURL +"/"+ Jeeves.Prefix.SERVICE +"/en";

		return "http://" + host + (port == "80" ? "" : ":" + port) + locServ;
	}

	//--------------------------------------------------------------------------

	public String autodetectSchema(Element md)
	{
		Namespace nons= Namespace.NO_NAMESPACE;
		Namespace gmd = Namespace.getNamespace("http://www.isotc211.org/2005/gmd");

		if (md.getName().equals("MD_Metadata") && md.getNamespace().equals(gmd))
			return "iso19139";

		if (md.getNamespace().equals(nons))
		{
			if (md.getName().equals("Metadata"))
				return "iso19115";

			if (md.getName().equals("simpledc"))
				return "dublin-core";

			if (md.getName().equals("metadata"))
				return "fgdc-std";
		}

		return null;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Metadata Insert API
	//---
	//--------------------------------------------------------------------------

	/** Create a new metadata duplicating an existing template
	  */

	public String createMetadata(Dbms dbms, String templateId, String groupOwner,
										  SerialFactory sf, String source, int owner) throws Exception
	{
		String query = "SELECT schemaId, data FROM Metadata WHERE id="+ templateId;

		List listTempl = dbms.select(query).getChildren();

		if (listTempl.size() == 0)
			throw new IllegalArgumentException("Template id not found : " + templateId);

		Element el = (Element) listTempl.get(0);

		String schema = el.getChildText("schemaid");
		String data   = el.getChildText("data");
		String uuid   = UUID.randomUUID().toString();

		//--- generate a new metadata id
		int serial = sf.getSerial(dbms, "Metadata");

		Element xml = updateFixedInfo(schema, Integer.toString(serial), Xml.loadString(data, false), uuid);

		//--- store metadata

		String id = XmlSerializer.insert(dbms, schema, xml, serial, source, uuid, owner, groupOwner);

		copyDefaultPrivForGroup(dbms, id, groupOwner);

		//--- store metadata categories copying them from the template

		List categList = dbms.select("SELECT categoryId FROM MetadataCateg WHERE metadataId = "+templateId).getChildren();

		for(int i=0; i<categList.size(); i++)
		{
			Element elRec = (Element) categList.get(i);

			String catId = elRec.getChildText("categoryid");

			setCategory(dbms, id, catId);
		}

		//--- index metadata and exit

		indexMetadata(dbms, id);

		return id;
	}

	//--------------------------------------------------------------------------
	/** Adds a metadata in xml form (the xml should be validated). This method is
	  * used to add a metadata got from a remote site. Note that neighter permissions
	  * nor lucene indexes are updated.
	  */

	public String insertMetadataExt(Dbms dbms, String schema, Element md, SerialFactory sf,
											  String source, String createDate, String changeDate,
											  String uuid, int owner, String groupOwner) throws Exception
	{
		//--- generate a new metadata id
		int id = sf.getSerial(dbms, "Metadata");

		return insertMetadataExt(dbms, schema, md, id, source, createDate, changeDate, uuid,
										 owner, groupOwner);
	}

	//--------------------------------------------------------------------------
	/** @param source the source of the metadata. If null, the local siteId will be used
	  */

	public String insertMetadataExt(Dbms dbms, String schema, Element md, int id,
											  String source, String createDate, String changeDate,
											  String uuid, int owner, String groupOwner) throws Exception
	{
		if (source == null)
			source = getSiteID();

		//--- force namespace prefix for iso19139 metadata
		setNamespacePrefix(md);

		//--- Note: we cannot index metadata here. Indexing is done in the harvesting part

		return XmlSerializer.insert(dbms, schema, md, id, source, uuid, createDate,
											 changeDate, "n", null, owner, groupOwner);
	}

	//--------------------------------------------------------------------------
	/** Adds a metadata in xml form (the xml should be validated). The group id is
	  * used to setup permissions. Internal metadata fields are updated. Default
	  * operations are set.
	  */

	public String insertMetadata(Dbms dbms, String schema, String groupId, Element xml,
										  SerialFactory sf, String source, String uuid, int owner) throws Exception
	{
		return insertMetadata(dbms, schema, groupId, xml, sf, source, uuid, "n", null, owner);
	}

	//--------------------------------------------------------------------------

	public String insertMetadata(Dbms dbms, String schema, String groupOwner, Element xml,
										  SerialFactory sf, String source, String uuid, String isTemplate,
										  String title, int owner) throws Exception
	{
		//--- generate a new metadata id
		int serial = sf.getSerial(dbms, "Metadata");

		if (isTemplate.equals("n"))
			xml = updateFixedInfo(schema, Integer.toString(serial), xml, uuid);

		//--- force namespace prefix for iso19139 metadata
		setNamespacePrefix(xml);

		//--- store metadata

		String id = XmlSerializer.insert(dbms, schema, xml, serial, source, uuid, isTemplate, title, owner, groupOwner);

		copyDefaultPrivForGroup(dbms, id, groupOwner);
		indexMetadata(dbms, id);

		return id;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Metadata Get API
	//---
	//--------------------------------------------------------------------------

	/** Retrieves a metadata (in xml) given its id; adds editing information if needed
	  */

	public Element getMetadata(ServiceContext srvContext, String id, boolean forEditing) throws Exception
	{
		Dbms dbms = (Dbms) srvContext.getResourceManager().open(Geonet.Res.MAIN_DB);

		Element md = XmlSerializer.select(dbms, "Metadata", id);

		if (md == null)
			return null;

		String version = null;

		if (forEditing)
		{
			String schema = getMetadataSchema(dbms, id);
			version = editLib.addEditingInfo(schema, id, md);
		}

		md.addNamespaceDeclaration(Edit.NAMESPACE);

		Element info = buildInfoElem(srvContext, id, version);
		md.addContent(info);

		md.detach();
		return md;
	}

	//--------------------------------------------------------------------------
	/** Retrieves a metadata element given it's ref
	 */

	public Element getElementByRef(Element md, String ref)
	{
		return editLib.findElement(md, ref);
	}

	//--------------------------------------------------------------------------
	/** Returns true if the metadata exists in the database
	  */

	public boolean existsMetadata(Dbms dbms, String id) throws Exception
	{
		//FIXME : should use lucene

		List list = dbms.select("SELECT id FROM Metadata WHERE id="+ id).getChildren();
		return list.size() != 0;
	}

	//--------------------------------------------------------------------------
	/** Returns all the keywords in the system
	  */

	public Element getKeywords() throws Exception
	{
		Vector keywords = searchMan.getTerms("keyword");

		Element el = new Element("keywords");

		for(int i=0; i<keywords.size(); i++)
			el.addContent(new Element("keyword").setText((String)keywords.get(i)));

		return el;
	}

	//--------------------------------------------------------------------------
	//---
	//--- Metadata Update API
	//---
	//--------------------------------------------------------------------------

	/** For Editing : adds an element to a metadata ([add] link)
	  */

	public synchronized boolean addElement(Dbms dbms, String id, String ref, String name,
														String childName, String currVersion) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted
		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--- get element to add
		Element el = editLib.findElement(md, ref);

		if (el == null)
			throw new IllegalStateException("Element not found at ref = " + ref);

		//--- remove editing info added by previous call

		editLib.removeEditingInfo(md);
		String  schema = getMetadataSchema(dbms, id);

		if (childName.startsWith("_s"))
		{
			// add subtemplate
			String sid = childName.substring(2);
			Element subtemplate = XmlSerializer.select(dbms, "Metadata", sid);
			el.addContent(subtemplate);
		}
		else
		{
			// normal element
			Element child  = editLib.addElement(schema, el, name);
			if (!childName.equals(""))
			{
				// or element
				String uChildName = editLib.getUnqualifiedName(childName);
        String prefix     = editLib.getPrefix(childName);
        String ns         = editLib.getNamespace(childName,md);
        if (prefix.equals("")) {
           prefix = editLib.getPrefix(el.getName());
           ns = editLib.getNamespace(el.getName(),md);
        }
        Element orChild = new Element(uChildName,prefix,ns);
        child.addContent(orChild);

        //--- add mandatory sub-tags
        editLib.fillElement(schema, child, orChild);
			}
		}
		md = updateFixedInfo(schema, id, md, dbms);
		XmlSerializer.update(dbms, id, md);

		//--- update search criteria
		indexMetadata(dbms, id);

		return true;
	}

	//--------------------------------------------------------------------------

	public synchronized boolean addAttribute(Dbms dbms, String id, String ref,
														  String name, String currVersion) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted
		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--- get element to add
		Element el = editLib.findElement(md, ref);

		if (el == null)
			throw new IllegalStateException("Element not found at ref = " + ref);

		//--- remove editing info added by previous call
		editLib.removeEditingInfo(md);

		el.setAttribute(new Attribute(name, ""));

		String schema = getMetadataSchema(dbms, id);

		md = updateFixedInfo(schema, id, md, dbms);
		XmlSerializer.update(dbms, id, md);

		//--- update search criteria
		indexMetadata(dbms, id);

		return true;
	}

	//--------------------------------------------------------------------------
	/** For Editing : removes an element from a metadata ([del] link)
	  */

	public synchronized boolean deleteElement(Dbms dbms, String id, String ref,
															String currVersion) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted
		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--- get element to remove
		Element el = editLib.findElement(md, ref);

		if (el == null)
			throw new IllegalStateException("Element not found at ref = " + ref);

		el.detach();

		//--- remove editing info added by previous call
		editLib.removeEditingInfo(md);

		String schema = getMetadataSchema(dbms, id);

		md = updateFixedInfo(schema, id, md, dbms);
		XmlSerializer.update(dbms, id, md);

		//--- update search criteria

		indexMetadata(dbms, id);

		return true;
	}

	//--------------------------------------------------------------------------
	/** For Editing : removes an attribute from a metadata ([del] link)
	  */

	public synchronized boolean deleteAttribute(Dbms dbms, String id, String ref,
															  String name, String currVersion) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted
		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--- get element to remove
		Element el = editLib.findElement(md, ref);

		if (el == null)
			throw new IllegalStateException("Element not found at ref = " + ref);

		//--- remove editing info added by previous call
		editLib.removeEditingInfo(md);

		el.removeAttribute(name);

		String schema = getMetadataSchema(dbms, id);

		md = updateFixedInfo(schema, id, md, dbms);
		XmlSerializer.update(dbms, id, md);

		//--- update search criteria
		indexMetadata(dbms, id);

		return true;
	}

	//--------------------------------------------------------------------------
	/** For Editing : swap a tag with one of its sibling ([up] and [down] links)
	  */

	public synchronized boolean swapElement(Dbms dbms, String id, String ref,
														 String currVersion, boolean down) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted

		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--- get element to swap

		Element elSwap = editLib.findElement(md, ref);

		if (elSwap == null)
			throw new IllegalStateException("Element not found at ref = " + ref);

		//--- remove editing info added by previous call
		editLib.removeEditingInfo(md);

		//--------------------------------------------------------------------
		//--- swap elements

		int iSwapIndex = -1;

		List list = ((Element) elSwap.getParent()).getChildren(elSwap.getName(), elSwap.getNamespace());

		for(int i=0; i<list.size(); i++)
			if (list.get(i) == elSwap)
			{
				iSwapIndex = i;
				break;
			}

		if (iSwapIndex == -1)
			throw new IllegalStateException("Index not found for element --> " + elSwap);

		if (down)	swapElements(elSwap, (Element) list.get(iSwapIndex +1));
			else		swapElements(elSwap, (Element) list.get(iSwapIndex -1));

		String schema = getMetadataSchema(dbms, id);

		md = updateFixedInfo(schema, id, md, dbms);
		XmlSerializer.update(dbms, id, md);

		return true;
	}

	//--------------------------------------------------------------------------
	/** For Editing : updates all leaves with new values
	  */

	public synchronized boolean updateMetadata(Dbms dbms, String id, String currVersion,
															 Hashtable changes, boolean validate) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		//--- check if the metadata has been deleted
		if (md == null)
			return false;

		editLib.enumerateTree(md);

		//--- check if the metadata has been modified from last time
		if (currVersion != null && !editLib.getVersion(id).equals(currVersion))
			return false;

		//--------------------------------------------------------------------
		//--- update elements

		for(Enumeration e=changes.keys(); e.hasMoreElements();)
		{
			String ref = ((String) e.nextElement()) .trim();
			String val = ((String) changes.get(ref)).trim();
			String attr= null;

			int at = ref.indexOf("_");
			if (at != -1)
			{
				attr = ref.substring(at +1);
				ref  = ref.substring(0, at);
			}
			Element el = editLib.findElement(md, ref);
			if (el == null)
				throw new IllegalStateException("Element not found at ref = " + ref);

			if (attr != null) {
// The following work-around decodes any attribute name that has a COLON in it
// The : is replaced by the word COLON in the xslt so that it can be processed
// by the XML Serializer when an update is submitted - the only situation
// where this is known to occur is in the gml schema (eg. gml:id) - a better
// solution may be required
				Integer indexColon = attr.indexOf("COLON");
        if (indexColon != -1) {
					String prefix = attr.substring(0,indexColon);
          String localname = attr.substring(indexColon + 5);
          String namespace = editLib.getNamespace(prefix+":"+localname,md);
					Namespace attrNS = Namespace.getNamespace(prefix,namespace);
          if (el.getAttribute(localname,attrNS) != null) {
            el.setAttribute(new Attribute(localname,val,attrNS));
          }
// End of work-around
        } else {
          if (el.getAttribute(attr) != null)
            el.setAttribute(new Attribute(attr, val));
        }
			}
			else
			{
				List content = el.getContent();

				for(int i=0; i<content.size(); i++)
				{
					if (content.get(i) instanceof Text)
					{
						el.removeContent((Text) content.get(i));
						i--;
					}
				}
				el.addContent(val);
			}
		}
		//--- remove editing info added by previous call
		editLib.removeEditingInfo(md);

		return updateMetadata(dbms, id, md, validate, currVersion);
	}

	//--------------------------------------------------------------------------

	public synchronized boolean updateMetadata(Dbms dbms, String id, Element md,
														 boolean validate, String version) throws Exception
	{
		//--- check if the metadata has been modified from last time
		if (version != null && !editLib.getVersion(id).equals(version))
			return false;

		//--- make sure that any lingering CHOICE_ELEMENTS are replaced with their
		//--- children
		editLib.replaceChoiceElements(md);

		String schema = getMetadataSchema(dbms, id);
		md = updateFixedInfo(schema, id, md, dbms);

		if (validate)
			validate(schema, md);

		XmlSerializer.update(dbms, id, md);

		//--- update search criteria
		indexMetadata(dbms, id);

		return true;
	}

	//--------------------------------------------------------------------------
	//--- Used by the harvesting procedure

	public void updateMetadataExt(Dbms dbms, String id, Element md, String changeDate)
											throws Exception
	{
		XmlSerializer.update(dbms, id, md, changeDate);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Metadata Delete API
	//---
	//--------------------------------------------------------------------------

	/** Removes a metadata
	  */

	public synchronized void deleteMetadata(Dbms dbms, String id) throws Exception
	{
		//--- remove operations
		deleteMetadataOper(dbms, id, false);

		//--- remove categories
		deleteAllMetadataCateg(dbms, id);

		//--- remove metadata
		XmlSerializer.delete(dbms, "Metadata", id);

		//--- update search criteria
		searchMan.delete("_id", id+"");
	}

	//--------------------------------------------------------------------------
	/** Remove all operations stored for a metadata
	  */

	public void deleteMetadataOper(Dbms dbms, String id, boolean skipAllIntranet) throws Exception
	{
		String query = "DELETE FROM OperationAllowed WHERE metadataId="+id;

		if (skipAllIntranet)
			query += " AND groupId>1";

		dbms.execute(query);
	}

	//--------------------------------------------------------------------------
	/** Remove all categories stored for a metadata
	  */

	public void deleteAllMetadataCateg(Dbms dbms, String id) throws Exception
	{
		String query = "DELETE FROM MetadataCateg WHERE metadataId="+id;

		dbms.execute(query);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Metadata thumbnail API
	//---
	//--------------------------------------------------------------------------

	public Element getThumbnails(Dbms dbms, String id) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		if (md == null)
			return null;

		md.detach();

		String schema = getMetadataSchema(dbms, id);

		//--- do an XSL  transformation

		String styleSheet = editLib.getSchemaDir(schema) + Geonet.File.EXTRACT_THUMBNAILS;

		Element result = Xml.transform(md, styleSheet);
		result.addContent(new Element("id").setText(id));

		return result;
	}

	//--------------------------------------------------------------------------

	public void setThumbnail(Dbms dbms, String id, boolean small, String file) throws Exception
	{
		int    pos = file.lastIndexOf(".");
		String ext = (pos == -1) ? "???" : file.substring(pos +1);

		Element env = new Element("env");
		env.addContent(new Element("file").setText(file));
		env.addContent(new Element("ext").setText(ext));

		manageThumbnail(dbms, id, small, env, Geonet.File.SET_THUMBNAIL);
	}

	//--------------------------------------------------------------------------

	public void unsetThumbnail(Dbms dbms, String id, boolean small) throws Exception
	{
		Element env = new Element("env");

		manageThumbnail(dbms, id, small, env, Geonet.File.UNSET_THUMBNAIL);
	}

	//--------------------------------------------------------------------------

	private void manageThumbnail(Dbms dbms, String id, boolean small, Element env,
										  String styleSheet) throws Exception
	{
		Element md = XmlSerializer.select(dbms, "Metadata", id);

		if (md == null)
			return;

		md.detach();

		String schema = getMetadataSchema(dbms, id);

		//-----------------------------------------------------------------------
		//--- remove thumbnail from metadata

		//--- setup environment

		String type = small ? "thumbnail" : "large_thumbnail";

		env.addContent(new Element("type").setText(type));

		//--- setup root element

		Element root = new Element("root");
		root.addContent(md);
		root.addContent(env);

		//--- do an XSL  transformation

		styleSheet = editLib.getSchemaDir(schema) + styleSheet;

		md = Xml.transform(root, styleSheet);
		XmlSerializer.update(dbms, id, md);

		//--- update search criteria
		indexMetadata(dbms, id);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Privileges API
	//---
	//--------------------------------------------------------------------------

	/** Adds a permission to a group. Metadata is not reindexed
	  */

	public void setOperation(Dbms dbms, String mdId, String grpId, String opId) throws Exception
	{
		Object args[] = { new Integer(mdId), new Integer(grpId), new Integer(opId) };

		String query = "SELECT metadataId FROM OperationAllowed " +
							"WHERE metadataId=? AND groupId=? AND operationId=?";

		Element elRes = dbms.select(query, args);

		if (elRes.getChildren().size() == 0)
			dbms.execute("INSERT INTO OperationAllowed(metadataId, groupId, operationId) " +
							 "VALUES(?,?,?)", args);
	}

	//--------------------------------------------------------------------------

	public void unsetOperation(Dbms dbms, int mdId, int groupId, int operId) throws Exception
	{
		String query = "DELETE FROM OperationAllowed "+
							"WHERE metadataId=? AND groupId=? AND operationId=?";

		dbms.execute(query, mdId, groupId, operId);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Categories API
	//---
	//--------------------------------------------------------------------------

	/** Adds a category to a metadata. Metadata is not reindexed
	  */

	public void setCategory(Dbms dbms, String mdId, String categId) throws Exception
	{
		Object args[] = { new Integer(mdId), new Integer(categId) };

		if (!isCategorySet(dbms, mdId, categId))
			dbms.execute("INSERT INTO MetadataCateg(metadataId, categoryId) VALUES(?,?)", args);
	}

	//--------------------------------------------------------------------------

	public boolean isCategorySet(Dbms dbms, String mdId, String categId) throws Exception
	{
		String query = "SELECT metadataId FROM MetadataCateg " +"WHERE metadataId=? AND categoryId=?";

		Element elRes = dbms.select(query, new Integer(mdId), new Integer(categId));

		return (elRes.getChildren().size() != 0);
	}

	//--------------------------------------------------------------------------

	public void unsetCategory(Dbms dbms, String mdId, String categId) throws Exception
	{
		String query = "DELETE FROM MetadataCateg WHERE metadataId=? AND categoryId=?";

		dbms.execute(query, new Integer(mdId), new Integer(categId));
	}

	//--------------------------------------------------------------------------

	public Element getCategories(Dbms dbms, String mdId) throws Exception
	{
		String query = "SELECT id, name FROM Categories, MetadataCateg "+
							"WHERE id=categoryId AND metadataId=?";

		return dbms.select(query, new Integer(mdId));
	}

	//--------------------------------------------------------------------------
	//---
	//--- Private methods
	//---
	//--------------------------------------------------------------------------

	/** Used for editing : swaps children of 2 tags
	  */

	private void swapElements(Element el1, Element el2)
	{
		if (el1.getChildren().size() != 0)
		{
			Vector v1 = collectElements(el1);
			Vector v2 = collectElements(el2);

			addElements(el1, v2);
			addElements(el2, v1);
		}
		else
		{
			//--- swap text

			String sValue1 = el1.getText();
			String sValue2 = el2.getText();

			el1.setText(sValue2);
			el2.setText(sValue1);
		}
	}

	//--------------------------------------------------------------------------
	/** Collects all children of a tag, removing them from the parent
	  */

	private Vector collectElements(Element el)
	{
		Vector v = new Vector();

		List list = el.getChildren();

		for(int i=0; i<list.size(); i++)
			v.add(list.get(i));

		el.removeContent();

		return v;
	}

	//--------------------------------------------------------------------------
	/** Add all tags in a vector to a tag
	  */

	private void addElements(Element el, Vector v)
	{
		for(int i=0; i<v.size(); i++)
			el.addContent((Element) v.get(i));
	}

	//--------------------------------------------------------------------------

	private Element updateFixedInfo(String schema, String id, Element md, Dbms dbms) throws Exception
	{
		String query = "SELECT uuid, source, isTemplate FROM Metadata WHERE id = " + id;

		Element rec = dbms.select(query).getChild("record");
		String isTemplate = rec.getChildText("istemplate");

		// don't process templates
		if (isTemplate.equals("n"))
		{
			String uuid = rec.getChildText("uuid");

			return updateFixedInfo(schema, id, md, uuid);
		}
		else return md;
	}

	//--------------------------------------------------------------------------

	public Element updateFixedInfo(String schema, String id, Element md, String uuid) throws Exception
	{
		//--- setup environment

		Element env = new Element("env");

		env.addContent(new Element("id")        .setText(id));
		env.addContent(new Element("uuid")      .setText(uuid));
		env.addContent(new Element("changeDate").setText(new ISODate().toString()));
		env.addContent(new Element("siteURL")   .setText(getSiteURL()));

		//--- setup root element

		Element root = new Element("root");
		root.addContent(md);
		root.addContent(env);

		//--- do an XSL  transformation

		String styleSheet = editLib.getSchemaDir(schema) + Geonet.File.UPDATE_FIXED_INFO;

		return Xml.transform(root, styleSheet);
	}

	//--------------------------------------------------------------------------

	private Element buildInfoElem(ServiceContext context, String id, String version) throws Exception
	{
		Dbms dbms = (Dbms) context.getResourceManager().open(Geonet.Res.MAIN_DB);

		String query ="SELECT schemaId, createDate, changeDate, source, isTemplate, title, "+
									"uuid, isHarvested FROM Metadata WHERE id = " + id;

		// add Metadata table infos: schemaId, createDate, changeDate, source,
		Element rec = dbms.select(query).getChild("record");

		String  schema     = rec.getChildText("schemaid");
		String  createDate = rec.getChildText("createdate");
		String  changeDate = rec.getChildText("changedate");
		String  source     = rec.getChildText("source");
		String  isTemplate = rec.getChildText("istemplate");
		String  title      = rec.getChildText("title");
		String  uuid       = rec.getChildText("uuid");
		String  isHarvested= rec.getChildText("isharvested");

		Element info = new Element(Edit.RootChild.INFO, Edit.NAMESPACE);

		addElement(info, Edit.Info.Elem.ID,          id);
		addElement(info, Edit.Info.Elem.SCHEMA,      schema);
		addElement(info, Edit.Info.Elem.CREATE_DATE, createDate);
		addElement(info, Edit.Info.Elem.CHANGE_DATE, changeDate);
		addElement(info, Edit.Info.Elem.IS_TEMPLATE, isTemplate);
		addElement(info, Edit.Info.Elem.TITLE,       title);
		addElement(info, Edit.Info.Elem.SOURCE,      source);
		addElement(info, Edit.Info.Elem.UUID,        uuid);
		addElement(info, Edit.Info.Elem.IS_HARVESTED,isHarvested);

		if (version != null)
			addElement(info, Edit.Info.Elem.VERSION, version);

		// add operations
		HashSet hsOper = accessMan.getOperations(context, id, context.getIpAddress());

		addElement(info, Edit.Info.Elem.VIEW,     String.valueOf(hsOper.contains(AccessManager.OPER_VIEW)));
		addElement(info, Edit.Info.Elem.NOTIFY,   String.valueOf(hsOper.contains(AccessManager.OPER_NOTIFY)));
		addElement(info, Edit.Info.Elem.DOWNLOAD, String.valueOf(hsOper.contains(AccessManager.OPER_DOWNLOAD)));
		addElement(info, Edit.Info.Elem.DYNAMIC,  String.valueOf(hsOper.contains(AccessManager.OPER_DYNAMIC)));
		addElement(info, Edit.Info.Elem.FEATURED, String.valueOf(hsOper.contains(AccessManager.OPER_FEATURED)));

		if (accessMan.canEdit(context, id))
			addElement(info, Edit.Info.Elem.EDIT, "true");

		// add categories
		List categories = dbms.select("SELECT id, name FROM MetadataCateg, Categories "+
												"WHERE metadataId = " + id + " AND categoryId = id ORDER BY id").getChildren();

		for (Iterator iter = categories.iterator(); iter.hasNext(); )
		{
			Element category = (Element)iter.next();
			addElement(info, Edit.Info.Elem.CATEGORY, category.getChildText("name"));
		}
		return info;
	}

	//--------------------------------------------------------------------------

	private static void addElement(Element root, String name, String value)
	{
		root.addContent(new Element(name).setText(value));
	}

	//--------------------------------------------------------------------------

	private String getMetadataSchema(Dbms dbms, String id) throws Exception
	{
		List list = dbms.select("SELECT schemaId FROM Metadata WHERE id = " +id).getChildren();

		if (list.size() == 0)
			throw new IllegalArgumentException("Metadata not found for id : " +id);
		else
		{
			// get metadata
			Element record = (Element) list.get(0);
			return record.getChildText("schemaid");
		}
	}

	//--------------------------------------------------------------------------

	private void copyDefaultPrivForGroup(Dbms dbms, String id, String groupId) throws Exception
	{
		//--- store access operations for group

		setOperation(dbms, id, groupId, AccessManager.OPER_VIEW);
		setOperation(dbms, id, groupId, AccessManager.OPER_DOWNLOAD);
		setOperation(dbms, id, groupId, AccessManager.OPER_NOTIFY);
		setOperation(dbms, id, groupId, AccessManager.OPER_DYNAMIC);
	}

	//--------------------------------------------------------------------------

	private String getSiteID()
	{
		return settingMan.getValue("system/site/siteId");
	}

	//---------------------------------------------------------------------------
	//---
	//--- Static methods
	//---
	//---------------------------------------------------------------------------

	public static void setNamespacePrefix(Element md)
	{
		//--- if the metadata has no namespace or already has a namespace then
		//--- we must skip this phase

		Namespace ns = md.getNamespace();
    // System.out.println("DM: Namespace prefix is '"+md.getNamespacePrefix()+"'"); // DEBUG
    if (ns == Namespace.NO_NAMESPACE || (!md.getNamespacePrefix().equals("")))
      return;


		//--- set prefix for iso19139 metadata

		ns = Namespace.getNamespace("gmd", md.getNamespace().getURI());
		setNamespacePrefix(md, ns);
	}

	//---------------------------------------------------------------------------

	private static void setNamespacePrefix(Element md, Namespace ns)
	{
		if (md.getNamespaceURI().equals(ns.getURI()))
			md.setNamespace(ns);

		for (Object o : md.getChildren())
			setNamespacePrefix((Element) o, ns);
	}

	//--------------------------------------------------------------------------
	//---
	//--- Variables
	//---
	//--------------------------------------------------------------------------

	private String baseURL;

	private EditLib editLib = new EditLib(this);

	private AccessManager  accessMan;
	private SearchManager  searchMan;
	private SettingManager settingMan;

}

//=============================================================================

