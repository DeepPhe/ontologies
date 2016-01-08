package org.healthnlp.deepphe.ontologies;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.pitt.dbmi.nlp.noble.ontology.*;
import edu.pitt.dbmi.nlp.noble.ontology.owl.OOntology;

/**
 * create a copy of ontology with all URIS be complient with realist onotlogy standards
 * @author tseytlin
 *
 */
public class ConvertOntologyURIs {
	public static final String URI_FORMAT =  "DP%06d";
	public static final String BASE_URL_PREFIX = "http://ontologies.dbmi.pitt.edu";
	private Map<IOntology,IProperty> seeAlsoMap;
	private int index;
	
	
	private IProperty getSeeAlso(IOntology ont){
		if(seeAlsoMap == null)
			seeAlsoMap = new HashMap<IOntology, IProperty>();
		if(seeAlsoMap.containsKey(ont))
			return seeAlsoMap.get(ont);
		IProperty p = ont.getProperty(IProperty.RDFS_SEE_ALSO);
		if(p == null){
			p = ont.createProperty("http://www.w3.org/2000/01/rdf-schema#seeAlso",IProperty.ANNOTATION);
		}
		
		seeAlsoMap.put(ont,p);
		return p;
	}
	
	/**
	 * create new name for an existing resource
	 * @param source
	 * @return
	 */
	private String createNewResourceName(IResource source,IOntology target){
		// check if existing resource already has an owl:seeAlso
		for(Object nm : source.getPropertyValues(getSeeAlso(source.getOntology()))){
			if(nm.toString().startsWith(BASE_URL_PREFIX)){
				try {
					URI u = new URI(nm.toString());
					return u.getFragment();
				} catch (URISyntaxException e) {
					e.printStackTrace();
				}
			}
		}
		
		// check if there is already resource with a given name
		String name = null;
		do{
			name = String.format(URI_FORMAT,index++);
		}while(target.hasResource(name));
		return name;
	}
	
	/**
	 * copy property to new ontology
	 * @param source
	 * @param target
	 * @return
	 */
	private IProperty copyProperty(IProperty sp, IOntology target){
		String name = createNewResourceName(sp,target);
		
		// if property was already created then just return it
		if(target.hasResource(name))
			return target.getProperty(name);
		
		IProperty tp = target.createProperty(name,sp.getPropertyType());
		String [] labels = sp.getLabels();
		if(labels.length == 0){
			tp.addLabel(sp.getName());
		}
		// transfer all properties
		for(IProperty p : sp.getProperties()){
			for(Object o: sp.getPropertyValues(p)){
				tp.addPropertyValue(p,o);
			}
		}
		// transfer all super properties
		for(IProperty p: sp.getDirectSuperProperties()){
			tp.addSuperProperty(copyProperty(p, target));
			if(p.getPropertyType() == IProperty.DATATYPE)
				tp.removeSuperProperty(((OOntology)target).getTopDatatProperty());
			else if(p.getPropertyType() == IProperty.OBJECT)
				tp.removeSuperProperty(((OOntology)target).getTopObjectProperty());
		}
		
		// transfer domain
		List<IClass> domain = new ArrayList<IClass>();
		for(IClass c: sp.getDomain()){
			domain.add(copyClass(c,target));
		}
		if(!domain.isEmpty())
			tp.setDomain(domain.toArray(new IClass [0]));
		
		
		// transfer range
		List range = new ArrayList();
		for(Object o: sp.getRange()){
			if(o instanceof IClass)
				range.add(copyClass((IClass)o,target));
			else
				range.add(o);
		}
		if(!range.isEmpty())
			tp.setRange(range.toArray());
		
		// set properties
		tp.setFunctional(sp.isFunctional());
		if(tp.getPropertyType() == IProperty.OBJECT){
			tp.setSymmetric(sp.isSymmetric());
			tp.setTransitive(sp.isTransitive());
		}
		
			
		// save seeAlso property
		clearSeeAlso(tp);
		clearSeeAlso(sp);
		tp.addPropertyValue(getSeeAlso(tp.getOntology()),""+sp.getURI());
		sp.addPropertyValue(getSeeAlso(sp.getOntology()),""+tp.getURI());
		
		return tp;
	}
	
	/**
	 * copy or fetch individual class
	 * @param scls
	 * @param target
	 * @return
	 */

	private IClass copyClass(IClass scls, IOntology target) {
		String name = createNewResourceName(scls,target);
		
		// return thing
		if(scls.getOntology().getRoot().equals(scls))
			return target.getRoot();
		
		// if property was already created then just return it
		if(target.hasResource(name))
			return target.getClass(name);
		
		// create new class
		IClass tcls = target.createClass(name);
		String [] labels = scls.getLabels();
		if(labels.length == 0){
			tcls.addLabel(scls.getName());
		}
		// transfer all properties
		for(IProperty p : scls.getProperties()){
			for(Object o: scls.getPropertyValues(p)){
				//TODO: what about defined annotation properties
				tcls.addPropertyValue(p,o);
			}
		}
		
		// transfer all super classes
		for(IClass p: scls.getDirectSuperClasses()){
			tcls.addSuperClass(copyClass(p,target));
			tcls.removeSuperClass(target.getRoot());
		}
		
		// transfer all equivalent classes
		for(IClass eq: scls.getEquivalentClasses()){
			if(!eq.equals(scls))
				tcls.addEquivalentClass(copyClass(eq,target));
		}
		
		// transfer all disjoint classes
		for(IClass dc: scls.getDisjointClasses()){
			tcls.addDisjointClass(copyClass(dc,target));
		}

					
		// save seeAlso property
		clearSeeAlso(tcls);
		clearSeeAlso(scls);
		tcls.addPropertyValue(getSeeAlso(tcls.getOntology()),""+scls.getURI());
		scls.addPropertyValue(getSeeAlso(scls.getOntology()),""+tcls.getURI());
		
		return tcls;
	}
	
	private void clearSeeAlso(IResource cls){
		IProperty p = getSeeAlso(cls.getOntology());
		for(Object o: cls.getPropertyValues(p)){
			if(o.toString().startsWith(BASE_URL_PREFIX)){
				cls.removePropertyValue(p,o);
			}
		}
	}
	
	
	
	private IRestriction copyRestriction(IRestriction sr, IOntology target){
		IRestriction tr = target.createRestriction(sr.getRestrictionType());
		tr.setProperty(copyProperty(sr.getProperty(), target));
		tr.setParameter(copyExpression(sr.getParameter(), target));
		return tr;
	}
	
	/**
	 * copy restriction
	 * @param exp
	 * @param target
	 * @return
	 */
	private ILogicExpression copyExpression(ILogicExpression sexp, IOntology target) {
		ILogicExpression texp = new LogicExpression(sexp.getExpressionType());
		for(Object o: sexp){
			if(o instanceof ILogicExpression){
				texp.add(copyExpression((ILogicExpression)o, target));
			}else if(o instanceof IRestriction){
				IRestriction sr = (IRestriction) o;
				texp.add(copyRestriction(sr, target));
			}else if(o instanceof IClass){
				texp.add(copyClass((IClass)o, target));
			}else{
				texp.add(o);
			}
		}
		return texp;
	}

	public void convert(File sourceFile, File targetDir, String targetURI) throws IOntologyException, URISyntaxException, FileNotFoundException{
		index = 1;
		IOntology source = OOntology.loadOntology(sourceFile);
		IOntology target = OOntology.createOntology(new URI(targetURI));
		System.out.println("converting: "+source.getURI());
		
	
		// what about imported ontologies?
		for(IOntology o: source.getImportedOntologies()){
			File f = null;
			for(File ff: targetDir.listFiles()){
				if(ff.getName().endsWith(".owl") && (o.getName().toLowerCase()+".owl").endsWith(ff.getName().toLowerCase())){
					f = ff;
					break;
				}
			}
			if(f != null){
				IOntology io = OOntology.loadOntology(f);
				target.addImportedOntology(io);
			}
		}
		
		
		// save ontology
		File targetOnt = new File(targetDir,target.getName()+".owl");
		if(!targetOnt.getParentFile().exists())
			targetOnt.getParentFile().mkdirs();
		
		System.out.println("saving: "+targetOnt.getAbsolutePath());
		target.write(new FileOutputStream(targetOnt),IOntology.OWL_FORMAT);
		
		// reload ontology (@Q#Q@#RW#$)!!!!
		// honestly this is FUCKED UP!!! I should not do that, BUT
		// I can't get OWL-API to actually load the model of the imported ontology
		// after I dynamically add an import.
		System.out.println("reloading: "+targetOnt.getAbsolutePath());
		target = OOntology.loadOntology(targetOnt);
		
	/*	System.out.println(target.hasResource("DP000011"));
		System.out.println(Arrays.asList(target.getResource("http://ontologies.dbmi.pitt.edu/deepphe/cancer/cancer.owl#DP000011").getLabels()));*/
		
		// transfer all properties
		for(IProperty p : source.getProperties()){
			for(Object o: source.getPropertyValues(p)){
				target.addPropertyValue(p,o);
			}
		}
		
		System.out.println("converting properties..");
		IResourceIterator props = source.getAllProperties();
		while(props.hasNext()){
			IProperty sp = (IProperty) props.next();
			if(sp != null){
				copyProperty(sp, target);
			}
		}

		System.out.println("converting classes..");
		IResourceIterator clss = source.getAllClasses();
		while(clss.hasNext()){
			IClass cls = (IClass) clss.next();
			if(cls != null){
				copyClass(cls, target);
			}
		}

		System.out.println("converting restrictions..");
		clss = source.getAllClasses();
		while(clss.hasNext()){
			IClass cls = (IClass) clss.next();
			if(cls != null){
				copyRestrictions(cls, target);
			}
		}
		
	
		
		// save ontology
		System.out.println("saving: "+targetOnt.getAbsolutePath());
		target.save();
		
		System.out.println("saving: "+source.getLocation());
		source.save();
	}
	


	private void copyRestrictions(IClass scls, IOntology target) {
		IClass tcls = copyClass(scls, target);
		// skip class that is defined in different ontology
		if(!tcls.getURI().toString().startsWith(target.getURI().toString()))
			return;
		
		
		
		// handle restrictions
		for(Object o :  scls.getEquivalentRestrictions()){
			if(o instanceof IRestriction){
				tcls.addEquivalentRestriction(copyRestriction((IRestriction)o,target));
			}else if(o instanceof ILogicExpression){
				tcls.addEquivalentRestriction(copyExpression((ILogicExpression)o,target));
			}
		}
		// handle restrictions
		for(Object o : scls.getDirectNecessaryRestrictions()){
			if(o instanceof IRestriction){
				tcls.addNecessaryRestriction(copyRestriction((IRestriction)o,target));
			}else if(o instanceof ILogicExpression){
				tcls.addNecessaryRestriction(copyExpression((ILogicExpression)o,target));
			}
		}
		
		
	}

	public static void main(String[] args) throws Exception {
		String sourceOntologyCancer = "/home/tseytlin/Work/ontologies/deepphe/modelCancer.owl";
		String targetURIcancer = "http://ontologies.dbmi.pitt.edu/deepphe/cancer/cancer.owl";
		String sourceOntologyBreast = "/home/tseytlin/Work/ontologies/deepphe/modelBreastCancer.owl";
		String targetURIbreast = BASE_URL_PREFIX+"/deepphe/cancer/breastCancer.owl";
		
		String targetDirectory = "/home/tseytlin/Output/ontologies/";
		
		ConvertOntologyURIs c = new ConvertOntologyURIs();
		c.convert(new File(sourceOntologyCancer),new File(targetDirectory),targetURIcancer);
		c.convert(new File(sourceOntologyBreast),new File(targetDirectory),targetURIbreast);
		System.out.println("ok");
	}

	
}
