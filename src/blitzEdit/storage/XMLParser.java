package blitzEdit.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import blitzEdit.core.BlueprintContainer;
import blitzEdit.core.Circuit;
import blitzEdit.core.Component;
import blitzEdit.core.ComponentBlueprint;
import blitzEdit.core.ComponentProperty;
import blitzEdit.core.Connector;
import blitzEdit.core.Element;
import tools.FileTools;
import tools.SvgRenderer;


/**
 * Parser that uses xml format to save and load circuits and load blueprints.
 * 
 * @author Chrisian Gartner
 */
public class XMLParser implements IParser{
	private Circuit currentCircuit = null;
	private ArrayList<Element> elements = new ArrayList<Element>();
	private boolean useHashes;
	private boolean ignoreHashes;
	
	private static final String xmlTag = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
	
	public void saveCircuit (Circuit circuit, String destination, boolean useHashes) 
	{
		currentCircuit = circuit;
		this.useHashes = useHashes;
		
		String circuitString = new String();
		
		// save elements in String
		elements = new ArrayList<Element>(currentCircuit.getElements());
		for(int i=0; i<elements.size();i++)
		{
			Element elem = elements.get(i);
			if(elem.getClass() == Component.class)
			{
				Component comp = (Component) elem;
				
				circuitString += "\t" + saveComponent((Component)elem, i);
				circuitString += "\t\t" + saveComponentChilds((Component)elem);
				
				ArrayList<Connector> connectors = comp.getConnectors();
				for(Connector connector : connectors)
				{
					circuitString += "\t\t" + saveConnector(connector, elements.indexOf(connector));
					if (connector.connected())
					{
						ArrayList<Connector> connections = connector.getConnections();
						for (Connector connectedConn : connections)
						{
							circuitString += "\t\t\t" + saveConnection(connector, connectedConn);
						}
					}
					circuitString += "\t\t" + "</connector>" + "\n";
				}
				circuitString += "\t" + "</component>" + "\n";
			}
		}
		
		String result = circuitString;
		if(useHashes)
		{
			result = "\t" + createCircuitHash(result) + result;
		}
		result = xmlTag + "\n" + "<Circuit>\n" + result + "</Circuit>";
			
		
		Path path = Paths.get(destination);
		// safe string in file
		try
		{
			FileOutputStream fos = new FileOutputStream(path.toString());
			fos.write(result.getBytes());
			fos.close();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public void loadCircuit (Circuit circuit, String filepath) 
	{
		currentCircuit = circuit;
	
		String fileString = null;
		try
		{
			fileString = FileTools.readFile(filepath, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			JOptionPane.showMessageDialog(null,
					"Error occured while trying to read the Circuit from " + filepath, "Loading Error",
					JOptionPane.CANCEL_OPTION);
		}
		
		String originalFileString = fileString; // used for hash check
		// remove unnecessary tokens
		fileString = fileString.replace(xmlTag, "");
		fileString = fileString.replace("</component>", "");
		fileString = fileString.replace("</connector>", "");
		fileString = fileString.replace("/>", "");
		fileString = fileString.replace(">", "");
		fileString = fileString.replace("\n", "");
		fileString = fileString.replace("\t", "");
		
		String [] xmlElements = fileString.split("<");
		
		ArrayList<String> components = new ArrayList<String>();
		ArrayList<String> connectors = new ArrayList<String>();
		ArrayList<String> connections = new ArrayList<String>();
		ArrayList<String> childs = new ArrayList<String>();
		
		for(String xml : xmlElements)
		{
			xml = xml.replace("\"", "");
			if(xml.startsWith("component"))
			{
				components.add(xml);
			}
			else if(xml.startsWith("connector"))
			{
				connectors.add(xml);
			}
			else if(xml.startsWith("connection"))
			{
				connections.add(xml);
			}
			else if(xml.startsWith("child"))
			{
				childs.add(xml);
			}
			else if(xml.startsWith("circuithash"))
			{
				String hash = xml.replace("circuithash hash=", "");
				if (!checkCircuitHash(hash, originalFileString))
				{
					int accepted = JOptionPane.showConfirmDialog(null,
							"Modifications have been made in this file. Do you still want to load it?", "Loading Error",
							JOptionPane.YES_NO_OPTION);
					if (accepted != 0) // declined loading with changes
					{
						return;
					}
					else // accepted loading with changes
					{
						ignoreHashes = true;
					}
				}
			}
		}
		
		// make sure elements array is large enough
		for (int i = 0; i < components.size() + connectors.size(); i++)
		{
			elements.add(null);
		}
		
		for(String xml : components)
		{
			if(!readComponent(xml))
			{
				System.err.println("Stopped loading process.");
				return;
			}
		}
		
		for(String xml : connectors)
		{
			if(!readConnector(xml))
			{
				System.err.println("Stopped loading process.");
				return;
			}
		}
		
		for(String xml : childs)
		{
			if(!addComponentChilds(xml))
			{
				System.err.println("Stopped loading process.");
				return;
			}
		}
		
		for(String xml : connections)
		{
			if(!createConnection(xml))
			{
				System.err.println("Stopped loading process.");
				return;
			}
		}

		// check for connectors without owner
		ArrayList<Element> elemsToRemove = new ArrayList<Element>();
		for (Element e : elements)
		{
			if (e.getClass() == Connector.class)
			{
				Connector c = (Connector) e;
				if (c.getOwner() == null)
				{
					System.err.println("Error in file: Connector " + elements.indexOf(e) + " has no owner.");
					elemsToRemove.add(e);
				}
			}
		}
		
		if(elemsToRemove.isEmpty() || elements.removeAll(elemsToRemove))
		{
			circuit.clearElements();
			circuit.addElements(elements);
		}
	}
	
	/**
	 * Reads an xml file and extracts the strings used for a ComponentBlueprint.
	 * Calls readBlueprintValues() to interpret the extracted strings
	 * Displays message if error occured.
	 * 
	 * 
	 * @param 	filepath			Contains filepath on disk
	 * @return	ComponentBlueprint	Created blueprint
	 */
	public static ComponentBlueprint readBlueprint(String filepath)
	{
		String fileString;
		File parent;
		
		try
		{
			File file = new File(filepath);
			parent = file.getParentFile();
			
			fileString = FileTools.readFile(filepath, StandardCharsets.UTF_8);
		}
		catch (IOException e)
		{
			JOptionPane.showConfirmDialog(null,
					"Error occured while trying to read a Blueprint from " + filepath + ".\n" + e.getLocalizedMessage(), "Loading Error",
					JOptionPane.CANCEL_OPTION);
			return null;
		}
		
		// remove unnecessary tokens
		fileString = fileString.replace(xmlTag, "");
		fileString = fileString.replace("</component>", "");
		fileString = fileString.replace("/>", "");
		fileString = fileString.replace(">", "");
		fileString = fileString.replace("\n", "");
		fileString = fileString.replace("\t", "");
		
		String [] xmlElements = fileString.split("<");
		
		String component = null;
		String svg = null;
		ArrayList<String> connectors = new ArrayList<String>();
		ArrayList<String> properties = new ArrayList<String>();
		
		for(String xml : xmlElements)
		{
			xml = xml.replace("\"", "");
			if(xml.startsWith("component"))
			{
				component = xml;
			}
			else if(xml.startsWith("svg"))
			{
				svg = xml;
			}
			else if(xml.startsWith("connector"))
			{
				connectors.add(xml);
			}
			else if(xml.startsWith("property"))
			{
				properties.add(xml);
			}
		}
		
		if(component != null && svg != null)
		{
			return readBlueprintValues(component, svg, connectors, properties, parent);
		}
		return null;
	}
	
	/**
	 * Reads the strings given as parameter for xml properties and creates a ComponentBlueprint with them.
	 * 
	 * @param component				Contains the xml for the component
	 * @param svg					Contains the xml for the svg image
	 * @param connectors			Contains the xml for the connectors
	 * @param properties			Contains the xml for the propertiers
	 * @param parentDirectory		Contains the parent directory
	 * @return ComponentBlueprint	Creted blueprint
	 */
	private static ComponentBlueprint readBlueprintValues(String component, String svg, ArrayList<String> connectors, ArrayList<String> properties, File parentDirectory)
	{
		String type = null;
		String svgFilePath = null;
		int[][] relPos = new int[connectors.size()][2];
		short[] relRot = new short[connectors.size()];
		int width = 0;
		int height = 0;
		ArrayList<ComponentProperty> propertyList = new ArrayList<ComponentProperty>();
		String propertyName = null;
		String propertyValue = "";
		ComponentProperty.Unit propertyUnit = null;
		ComponentProperty.PropType propertyType = null;
		
		if(component == null || svg == null || connectors == null
				|| component.isEmpty() || svg.isEmpty() || connectors.isEmpty())
		{
			return null;
		}
		
		// component values
		String[] elem = component.split(" ");
		for(String s : elem)
		{
			s = s.trim();
			if(s.contains("type="))
			{
				String[] sElem = s.split("=");
				type = sElem[1];
			}
		}
		
		// svg values
		elem = svg.split(" ");
		for (String s : elem)
		{
			s = s.trim();
			if (s.contains("path="))
			{
				String[] sElem = s.split("=");
				svgFilePath = sElem[1];
				
				File file = new File(svgFilePath);
				if(!file.exists())
				{
					file = new File(parentDirectory.toString() + "\\" + svgFilePath);
					if(!file.exists())
					{
						return null;
					}
				}
				svgFilePath = file.toString();
				String svgFileSting = SvgRenderer.getSvgFileString(svgFilePath);
				width = (int) SvgRenderer.getSvgWidth(svgFileSting);
				height = (int) SvgRenderer.getSvgHeight(svgFileSting);
			}
		}

		//connector values
		for (int i = 0; i<connectors.size(); i++)
		{
			String conn = connectors.get(i);
			elem = conn.split(" ");
			for (String s : elem)
			{
				s = s.trim();
				if (s.contains("x="))
				{
					String[] sElem = s.split("=");
					relPos[i][0] = Integer.parseInt(sElem[1]);
				}
				else if (s.contains("y="))
				{
					String[] sElem = s.split("=");
					relPos[i][1] = Integer.parseInt(sElem[1]);
				}
				else if (s.contains("rot="))
				{
					String[] sElem = s.split("=");
					relRot[i] = Short.parseShort(sElem[1]);
				}
			}
		}
		
		// property values
		for(String prop : properties)
		{
			elem = prop.split(" ");
			for(String s : elem)
			{
				s = s.trim();
				if(s.contains("type="))
				{
					String[] sElem = s.split("=");
					propertyType = ComponentProperty.toPropType(sElem[1]);
				}
				else if(s.contains("unit="))
				{
					String[] sElem = s.split("=");
					propertyUnit = ComponentProperty.toUnit(sElem[1]);
				}
				else if(s.contains("name="))
				{
					String[] sElem = s.split("=");
					propertyName = sElem[1];
				}
				else if(s.contains("value="))
				{
					String[] sElem = s.split("=");
					propertyValue = sElem[1];
				}
			}
			
			propertyList.add(new ComponentProperty(propertyName,propertyValue,propertyUnit,propertyType));
		}
		
		return new ComponentBlueprint(type, svgFilePath, relPos, relRot, width, height, propertyList);
	}
	
	/**
	 * Creates a component element out of the given xml string. Doesn't create connectors.
	 * 
	 * @param 	xml		Contains xml properties for the component
	 * @return	boolean	True if succesfully created component and added to elements array
	 */
	private boolean readComponent (String xml) 
	{
		int id=0;
		double x=0;
		double y=0;
		double rot=0;
		String type = null;
		String hash = null;
		boolean hasHash = false;
		
		String[] rectElements = xml.split(" ");
		for(String s : rectElements)
		{		
			s = s.trim();
			if(s.contains("id="))
			{
				String[] sElem = s.split("=");
				id = Integer.parseInt(sElem[1]);
			}
			if(s.contains("x="))
			{
				String[] sElem = s.split("=");
				x = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("y="))
			{
				String[] sElem = s.split("=");
				y = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("rot="))
			{
				String[] sElem = s.split("=");
				rot = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("type="))
			{
				String[] sElem = s.split("=");
				type = sElem[1];
			}
			else if(s.contains("hash="))
			{
				String[] sElem = s.split("=");
				hash = sElem[1];
				hasHash = true;
			}
			
		}
		
		if (!hasHash || (hasHash && checkHash(hash, xml)))
		{
			ComponentBlueprint bp = BlueprintContainer.get().getBlueprint(type);
			if (bp != null)
			{
				elements.set((int) id, bp.createComponentWithoutConnectors((int) x, (int) y, (short) rot));
				return true;
			}
			else
			{
				JOptionPane.showMessageDialog(null,
						"Missing blueprint component. Type: " + type, "Loading Error",
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
		else
		{
			System.err.println("Changes have been made in the component: " + xml + ". The component wasn't created.");
		}
		return false;
		
	}
	
	/**
	 * Creates a connector out of given xml string.
	 * 
	 * @param 	xml		Contains properties for connector
	 * @return	boolean	True if succesfully created connector and added to elements array
	 */
	private boolean readConnector (String xml)
	{
		int id=0;
		double x=0;
		double y=0;
		double rot=0;
		int relX=0;
		int relY=0;
		String hash = null;
		boolean hasHash = false;
		
		String[] rectElements = xml.split(" ");
		for(String s : rectElements)
		{		
			s = s.trim();
			if(s.contains("id="))
			{
				String[] sElem = s.split("=");
				id = Integer.parseInt(sElem[1]);
			}
			else if(s.contains("x="))
			{
				String[] sElem = s.split("=");
				x = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("y="))
			{
				String[] sElem = s.split("=");
				y = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("rot="))
			{
				String[] sElem = s.split("=");
				rot = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("relX="))
			{
				String[] sElem = s.split("=");
				relX = Integer.parseInt(sElem[1]);
			}
			else if(s.contains("relY="))
			{
				String[] sElem = s.split("=");
				relY = Integer.parseInt(sElem[1]);
			}
			else if(s.contains("hash="))
			{
				String[] sElem = s.split("=");
				hash = sElem[1];
				hasHash = true;
			}
		}
		int[] relPos = { relX, relY };

		if (!hasHash || (hasHash && checkHash(hash, xml)))
		{
			elements.set((int)id, new Connector((int) x, (int) y, relPos, (short) rot));
			return true;
		}
		else
		{
			System.err.println("Changes have been made in the connector: " + xml + ". The connector wasn't created.");
		}
		return false;
	}
	
	/**
	 * Creates a conenction out of the given xml string.
	 * 
	 * @param 	xml		Contains IDs for the both connectors to connect	
	 * @return	boolean	True if conenction between two connectors created
	 */
	private boolean createConnection(String xml)
	{
		double conn1=0;
		double conn2=0;
		String hash = null;
		boolean hasHash = false;
		
		String[] rectElements = xml.split(" ");
		for(String s : rectElements)
		{		
			s = s.trim();
			if(s.contains("conn1="))
			{
				String[] sElem = s.split("=");
				conn1 = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("conn2="))
			{
				String[] sElem = s.split("=");
				conn2 = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("hash="))
			{
				String[] sElem = s.split("=");
				hash = sElem[1];
				hasHash = true;
			}
		}

		if (!hasHash || (hasHash && checkHash(hash, xml)))
		{
			try
			{
				Connector connector1 = (Connector) elements.get((int) conn1);
				Connector connector2 = (Connector) elements.get((int) conn2);

				connector1.connect(connector2);
				return true;
			}
			catch (IndexOutOfBoundsException e)
			{
				System.err.println(
						"Error in generating connections between connectors: Tried to access " + e.getMessage());
			}
		}
		else
		{
			System.err.println("Changes have been made in the connection: " + xml + ". The connection wasn't created.");
		}
		return false;
	}
	
	/**
	 * Sets the owner of a connector to the component given in the xml string.
	 * 
	 * @param 	xml		Contains xml properties for owner and connector id
	 * @return	boolean	True if succesfully set the owner of the connector
	 */
	private boolean addComponentChilds(String xml)
	{
		double comp=0;
		ArrayList<Integer> conn= new ArrayList<Integer>();
		String hash = null;
		boolean hasHash = false;
		
		String[] rectElements = xml.split(" ");
		for(String s : rectElements)
		{		
			s = s.trim();
			if(s.contains("comp="))
			{
				String[] sElem = s.split("=");
				comp = Double.parseDouble(sElem[1]);
			}
			else if(s.contains("conn="))
			{
				String[] sElem = s.split("=");
				String[] sElem2 = sElem[1].split(";");
				for(String connectorId : sElem2)
				{
					conn.add(Integer.parseInt(connectorId));
				}
			}
			else if(s.contains("hash="))
			{
				String[] sElem = s.split("=");
				hash = sElem[1];
				hasHash = true;
			}
		}
		
		if (!hasHash || (hasHash && checkHash(hash, xml)))
		{
			if (!elements.isEmpty())
			{
				Component component = (Component) elements.get((int) comp);
				for (int i : conn)
				{
					component.addConnenctor((Connector) elements.get(i));
				}
				return true;
			}
		}
		else
		{
			System.err.println(
					"Changes have been made in the child property: " + xml + ". The connectors weren't added.");
		}
		return false;
	}
	
	/**
	 * Creates a string with the properties of a component. Adds a hash if hashes are activated.
	 * 
	 * @param 	comp	Component to save
	 * @param 	id		Position in the elements array
	 * @return	String	Contains the properties as string
	 */
	private String saveComponent(Component comp, int id)
	{
		String result = "component id=\"" + id + 
						"\" x=\"" + comp.getX() +
						"\" y=\"" + comp.getY() +
						"\" rot=\"" + comp.getRotation() +
						"\" type=\"" + comp.getType();
		
		if(useHashes)
		{
			result += "\" hash=\"" + createHash(result);
		}
		result = "<" +result + "\">\n";
		return result;
	}
	
	/**
	 * Creates a string with the properties of a connector. Adds a hash if hashes are activated.
	 * 
	 * @param 	conn	Connector to save
	 * @param 	id		Position in the elements array
	 * @return	String	Contains the properties as string
	 */
	private String saveConnector(Connector conn, int id)
	{
		int[] relPos = conn.getRelPos();
		
		String result = "connector id=\"" + id +
						"\" x=\"" + conn.getX() +
						"\" y=\"" + conn.getY() + 
						"\" rot=\"" + conn.getRelativeRotation() +
						"\" relX=\"" + relPos[0] +
						"\" relY=\"" + relPos[1] ;
		if(useHashes)
		{
			result += "\" hash=\"" + createHash(result);
		}
		result = "<" + result + "\">\n";
		return result;
	}
	
	/**
	 * Creates a string with IDs (index in elements array) of the two connectors. 
	 * Adds a hash if hashes are activated.
	 * 
	 * @param 	conn	1st connector
	 * @param 	conn2	2nd connector
	 * @return	String	Contains the connection as string
	 */
	private String saveConnection(Connector conn, Connector conn2)
	{
		String result = "connection conn1=\"" + elements.indexOf(conn) +
						"\" conn2=\"" + elements.indexOf(conn2);
		
		if(useHashes)
		{
			result += "\" hash=\"" + createHash(result);
		}
		result = "<" + result + "\"/>\n";
		return result;
	}
	
	/**
	 * Creates a string with the IDs of the component and its children. 
	 * Adds a hash if hashes are activated.
	 * 
	 * @param 	comp	Component used for save
	 * @return	String	Contains the component and its childs
	 */
	private String saveComponentChilds(Component comp)
	{
		String result;
		ArrayList<Connector> connectors = comp.getConnectors();
		
		result = "child comp=\"" + elements.indexOf(comp) + 
				 "\" conn=\"";
		
		for(Connector conn : connectors)
		{
			result += elements.indexOf(conn) + ";";
		}
		
		if(useHashes)
		{
			result += "\" hash=\"" + createHash(result);
		}
		result = "<" + result + "\"/>\n";
		return result;
	}
	
	/**
	 * If ignoreHash is false creates a hash with the given xml string without special characters and the hash.
	 * Checks the created hash against the given hash string afterwards.
	 * 
	 * @param 	hash	Hash code of the xml string
	 * @param 	xml		Xml string to check
	 * @return	boolean	True if hash matches the created xml hash
	 */
	private boolean checkHash(String hash, String xml)
	{
		if (!ignoreHashes)
		{
			xml = xml.replace("<", "");
			xml = xml.replace("/>", "");
			xml = xml.trim();
			xml = xml.replace("hash=", "");
			xml = xml.replace(hash, "");
			xml = xml.trim();

			try
			{
				int hashInt = Integer.parseInt(hash);
				int test = xml.hashCode();
				return (xml.hashCode() == hashInt);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Changes to the hash code have been made. " + e.getMessage());
				return false;
			}
		}
		else
		{
			return true;
		}
	}
	
	/**
	 * Uses String.hashCode() to generate a hash code for the xml string.
	 * @param 	xml		String to generate hash for
	 * @return	String	Contains hash code
	 */
	private String createHash(String xml)
	{
		xml = xml.replace("\"", "");
		return new String(Integer.toString(xml.hashCode()));
	}
	
	/**
	 * Deletes all special character and uses String.hashCode() afterwards.
	 * Inserts the hash into a xml property &lt;circuithash hash=""\&gt; and returns the string.
	 * @param 	xml		Xml used for saving
	 * @return	String	Contains the xml property with the created hash
	 */
	private String createCircuitHash(String xml)
	{
		xml = xml.replace("<", "");
		xml = xml.replace("/>", "");
		xml = xml.replace("\n", "");
		xml = xml.replace("\t", "");
		
		return new String("<circuithash hash=\"" + xml.hashCode() + "\"/>\n");
	}
	
	/**
	 * Removes all special characters, xmlTag and circuit xml tags and uses String.hashCode() afterwards.
	 * Then checks the given hash against the created.
	 * @param 	hash	Hash to check
	 * @param 	xml		Xml containing the circuit
	 * @return	boolean	True if hash matches
	 */
	private boolean checkCircuitHash(String hash, String xml)
	{
		xml = xml.replace(xmlTag, "");
		xml = xml.replace("</Circuit>", "");
		xml = xml.replace("<Circuit>", "");
		xml = xml.replace("<", "");
		xml = xml.replace("/>", "");
		xml = xml.replace("circuithash hash=\"" + hash + "\"", "");
		xml = xml.replace("\n", "");
		xml = xml.replace("\t", "");
		
		try{
			int hashInt = Integer.parseInt(hash);
			return (xml.hashCode() == hashInt);
		}catch (NumberFormatException e)
		{
			System.err.println("Changes to the hash code have been made. " + e.getMessage());
			return false;
		}
	}
}
