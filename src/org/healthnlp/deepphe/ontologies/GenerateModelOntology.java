package org.healthnlp.deepphe.ontologies;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.pitt.dbmi.nlp.noble.ontology.*;
import edu.pitt.dbmi.nlp.noble.ontology.owl.OOntology;
import edu.pitt.dbmi.nlp.noble.ontology.owl.OProperty;

public class GenerateModelOntology {
	public static final String URI_FORMAT =  "DP%06d";
	public static final String BASE_URL_PREFIX = "http://ontologies.dbmi.pitt.edu";
	private Map<IOntology,IProperty> seeAlsoMap;
	private Map<String,String> model2name,name2model;
	private int index;
	
	public static void main(String[] args) throws Exception {
		
		File sourceCancer = new File("/home/tseytlin/Work/ontologies/deepphe/nlpCancer.owl");
		File sourceBreastCancer = new File("/home/tseytlin/Work/ontologies/deepphe/nlpBreastCancer.owl");
		
		File modelCancer = new File("/home/tseytlin/Work/ontologies/cancer_models/cancer.owl");
		File targetBreastCancer = new File("/home/tseytlin/Work/ontologies/cancer_models/breastCancer.owl");
		
		GenerateModelOntology gm = new GenerateModelOntology();
		System.out.println("creating model ..");
		//gm.convertModel(sourceCancer,modelCancer);
		System.out.println("creating domain model ..");
		gm.convertDomainOntology(sourceBreastCancer,targetBreastCancer, modelCancer);
		System.out.println("done");
	}
	
	
	private void loadNameMap(IOntology ont){
		model2name = new HashMap<String, String>();
		name2model = new HashMap<String, String>();
		
		for(Iterator<IResource> it = ont.getAllResources();it.hasNext();){
			addResourceName(it.next());
		}
	}
	
	
	private void addResourceName(Object o){
		if(o instanceof IResource){
			IResource r = (IResource) o;
			String name = r.getName();
			String label = (r.getLabels().length > 0?r.getLabels()[0]:r.getName()).replaceAll("\\s+","");
			model2name.put(name,label);
			name2model.put(label,name);
		}
	}
	
	/**
	 * convert cancer model
	 * @param sourceCancer
	 * @param modelCancer
	 * @throws IOntologyException 
	 */
	
	private void convertModel(File sourceCancer, File modelCancer) throws IOntologyException {
		OOntology source = OOntology.loadOntology(sourceCancer);
		OOntology model = OOntology.loadOntology(modelCancer);
		
		// load name
		loadNameMap(model);
		
		// copy properties
		copyProperty(source.getTopDatatProperty(),model);
		copyProperty(source.getTopObjectProperty(),model);
		
		// copy classes
		for(IClass cls : source.getRoot().getDirectSubClasses())
			copyDomainClass(cls,model);
		
		
		// copy sub-classes into cancer template (if it is a leaf node)
		for(IClass cls: model.getRoot().getSubClasses()){
			// is it a leaf node?
			if(cls.getSubClasses().length == 0){
				IClass src = source.getClass(getDisplayName(cls));
				// does this class exists in the source
				if(src != null){
					for(IClass sc: src.getDirectSubClasses()){
						copyClass(sc,cls);
					}
				}
			}
		}
			
		
		// copy restrictions
		for(IClass cls: model.getRoot().getSubClasses()){
			// find matching class
			IClass src = source.getClass(getDisplayName(cls));
			if(src != null){
				copyRestrictions(src,cls);
			}
		}
		
		
		model.save();
	}

	
	private void copyRestrictions(IClass src, IClass target) {
		// handle restrictions
		for(Object o :  src.getEquivalentRestrictions()){
			if(o instanceof IRestriction){
				IRestriction r = copyRestriction((IRestriction)o,target.getOntology());
				if(r != null && !hasRestriction(target,r))
					target.addEquivalentRestriction(r);
				else 
					System.out.println("  skipping "+o);
			}else if(o instanceof ILogicExpression){
				target.addEquivalentRestriction(copyExpression((ILogicExpression)o,target.getOntology()));
			}
		}
		
		// handle restrictions
		for(Object o : src.getDirectNecessaryRestrictions()){
			if(o instanceof IRestriction){
				IRestriction r = copyRestriction((IRestriction)o,target.getOntology());
				if(r != null && !hasRestriction(target,r))
					target.addNecessaryRestriction(r);
				else 
					System.out.println("  skipping "+o);	
			}else if(o instanceof ILogicExpression){
				target.addNecessaryRestriction(copyExpression((ILogicExpression)o,target.getOntology()));
			}
		}
	}

	
	private boolean hasRestriction(IClass target, IRestriction r) {
		for(IRestriction rr : target.getRestrictions(r.getProperty())){
			if(rr.getParameter().equals(r.getParameter()))
				return true;
		}
		return false;
	}


	private IRestriction copyRestriction(IRestriction sr, IOntology target){
		ILogicExpression exp = copyExpression(sr.getParameter(), target);
		if(exp == null || exp.isEmpty())
			return null;
		IRestriction tr = target.createRestriction(sr.getRestrictionType());
		tr.setProperty(copyProperty(sr.getProperty(), target));
		tr.setParameter(exp);
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
				IClass c = getTargetClass((IClass)o, target);
				if(c != null)
					texp.add(c);
			}else{
				texp.add(o);
			}
		}
		return texp;
	}
	
	
	private String getDisplayName(IResource cls) {
		String name = cls.getName();
		if(cls.getLabels().length > 0)
			name = cls.getLabels()[0];
		return name.replaceAll("\\s+","");
	}
	
	private String getModelName(IResource cls) {
		return getModelName(cls.getName());
	}

	private String getModelName(String cls) {
		return name2model.get(cls);
	}
	
	/**
	 * copy classes from source to  model, but only if they have parents in model
	 * @param source
	 * @param root
	 */
	private IClass getTargetClass(IClass source, IOntology target) {
		String name = getModelName(source);
		if(name == null){
			return null;
		}
		
		// if property was already created then just return it
		if(target.hasResource(name))
			return target.getClass(name);
		
		return null;
	}
	
	/**
	 * copy classes from source to  model, but only if they have parents in model
	 * @param source
	 * @param root
	 */
	private IClass copyClass(IClass source, IClass modelParent) {
		IOntology target = modelParent.getOntology();
		String name = createNewResourceName(source,target);
		
		// return thing
		if(source.getOntology().getRoot().equals(source))
			return target.getRoot();
		
		// if property was already created then just return it
		if(target.hasResource(name))
			return target.getClass(name);
		
		//System.out.println("  copy "+source.getName());
	
		// create new class
		IClass tcls = modelParent.createSubClass(name);
		if(source.getLabels().length == 0){
			tcls.addLabel(source.getName());
		}else{
			for(String l: source.getLabels()){
				tcls.addLabel(l);
			}
		}
		
		// transfer all properties
		/*for(IProperty p : source.getProperties()){
			for(Object o: source.getPropertyValues(p)){
				//TODO: what about defined annotation properties
				tcls.addPropertyValue(p,o);
			}
		}*/
		
		// transfer all super classes
		/*for(IClass p: source.getDirectSuperClasses()){
			tcls.addSuperClass(copyClass(p,target));
			tcls.removeSuperClass(target.getRoot());
		}
		
		// transfer all equivalent classes
		for(IClass eq: source.getEquivalentClasses()){
			if(!eq.equals(source))
				tcls.addEquivalentClass(copyClass(eq,target));
		}
		
		// transfer all disjoint classes
		for(IClass dc: source.getDisjointClasses()){
			tcls.addDisjointClass(copyClass(dc,target));
		}*/

					
		// save seeAlso property
		clearSeeAlso(tcls);
		clearSeeAlso(source);
		tcls.addPropertyValue(getSeeAlso(tcls.getOntology()),""+source.getURI());
		source.addPropertyValue(getSeeAlso(source.getOntology()),""+tcls.getURI());
		
		// add resource name to map
		addResourceName(tcls);
		
		// recurse into children
		for(IClass sc: source.getDirectSubClasses()){
			copyClass(sc,tcls);
		}
		
		return tcls;
	}

	/**
	 * load names for a given ontology
	 * @param ont
	 * @return
	 
	private void loadNames(IOntology ont){
		if(model2name == null)
			model2name = new HashMap<String, String>();
		if(name2model == null)
			name2model = new HashMap<String, String>();
	}*/
	
	
	private boolean isIgnored(IResource r){
		IProperty ignore = r.getOntology().getProperty("ignore");
		return Boolean.parseBoolean(""+r.getPropertyValue(ignore));
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
		
		//check if there is a target resource with the same "name"
		if(getModelName(source) != null)
			return getModelName(source);
				
		// check if there is already resource with a given name
		String name = null;
		do{
			name = String.format(URI_FORMAT,index++);
		}while(target.hasResource(name));
		return name;
	}
	
	private void clearSeeAlso(IResource cls){
		IProperty p = getSeeAlso(cls.getOntology());
		for(Object o: cls.getPropertyValues(p)){
			if(o.toString().startsWith(BASE_URL_PREFIX)){
				cls.removePropertyValue(p,o);
			}
		}
	}
	
	/**
	 * copy property to new ontology
	 * @param source
	 * @param target
	 * @return
	 */
	private IProperty copyProperty(IProperty sp, IOntology target){
		if(isIgnored(sp))
			return null;
		String name = createNewResourceName(sp,target);
		
		// if property was already created then just return it
		if(target.hasResource(name))
			return target.getProperty(name);
		
		//System.out.println("  copy "+sp.getName());
		
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
			IProperty pp = copyProperty(p, target);
			if(pp != null)
				tp.addSuperProperty(pp);
			if(p.getPropertyType() == IProperty.DATATYPE)
				tp.removeSuperProperty(((OOntology)target).getTopDatatProperty());
			else if(p.getPropertyType() == IProperty.OBJECT)
				tp.removeSuperProperty(((OOntology)target).getTopObjectProperty());
		}
		
		// transfer domain
		/*List<IClass> domain = new ArrayList<IClass>();
		for(IClass c: sp.getDomain()){
			domain.add(copyClass(c,target));
		}
		if(!domain.isEmpty())
			tp.setDomain(domain.toArray(new IClass [0]));
		
		
		// transfer range
		List range = new ArrayList();
		for(Object o: sp.getRange()){
			if(o instanceof IClass){
				range.add(copyClass((IClass)o,target));
			}else{
				range.add(o);
			}
		}
		if(!range.isEmpty())
			tp.setRange(range.toArray());*/
		
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
		
		// add resource name to map
		addResourceName(tp);
		
		
		// recursively go into children
		for(IProperty cp : sp.getDirectSubProperties()){
			copyProperty(cp, target);
		}
		
		
		return tp;
	}

	
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
	
	
	private void convertDomainOntology(File sourceBreastCancer, File targetBreastCancer, File modelCancer) throws IOntologyException, URISyntaxException, FileNotFoundException {
		OOntology source = OOntology.loadOntology(sourceBreastCancer);
		OOntology model  = OOntology.loadOntology(modelCancer);
		OOntology target = OOntology.createOntology(new URI(BASE_URL_PREFIX+"/deepphe/cancer/"+targetBreastCancer.getName()));
	
		// add import
		target.addImportedOntology(model);
		target.write(new FileOutputStream(targetBreastCancer),IOntology.OWL_FORMAT);
		target.dispose();
		
		// reload target to trigger model load
		target = OOntology.loadOntology(targetBreastCancer);
		loadNameMap(target);
		
		// copy properties
		copyProperty(source.getTopDatatProperty(),target);
		copyProperty(source.getTopObjectProperty(),target);
		
		
		// copy classes
		for(IClass cls : source.getRoot().getDirectSubClasses())
			copyDomainClass(cls,target);
		
		
		// copy restrictions
		for(IClass cls: target.getRoot().getSubClasses()){
			// find matching class
			IClass src = source.getClass(getDisplayName(cls));
			if(src != null){
				copyRestrictions(src,cls);
			}
		}
		
		// save
		target.save();
		
	}


	private void copyDomainClass(IClass source, OOntology target) {
		// does this class has special annotation
		IProperty hasModel = source.getOntology().getProperty("hasModel");
		String parentName = (String) source.getPropertyValue(hasModel);
		if(parentName != null && getModelName(parentName) != null){
			IClass targetClass = target.getClass(getModelName(parentName));
			if(targetClass != null){
				copyClass(source,targetClass);
			}
		}
		
		// recurse
		for(IClass c : source.getDirectSubClasses()){
			copyDomainClass(c,target);
		}
	}

}
