package org.aksw.sparqlify.core.cast;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.aksw.commons.collections.MultiMaps;
import org.aksw.commons.collections.multimaps.BiHashMultimap;
import org.aksw.commons.collections.multimaps.IBiSetMultimap;
import org.aksw.sparqlify.algebra.sql.exprs2.SqlExpr;
import org.aksw.sparqlify.core.TypeToken;
import org.aksw.sparqlify.core.datatypes.SparqlFunction;
import org.aksw.sparqlify.core.datatypes.XClass;
import org.aksw.sparqlify.core.datatypes.XMethod;
import org.aksw.sparqlify.core.sql.expr.evaluation.SqlExprEvaluator;
import org.aksw.sparqlify.type_system.DirectSuperTypeProvider;
import org.aksw.sparqlify.type_system.DirectSuperTypeProviderBiSetMultimap;
import org.aksw.sparqlify.type_system.FunctionModel;
import org.aksw.sparqlify.type_system.FunctionModelAliased;
import org.aksw.sparqlify.type_system.FunctionModelImpl;
import org.aksw.sparqlify.type_system.FunctionModelMeta;
import org.aksw.sparqlify.type_system.TypeHierarchyUtils;
import org.aksw.sparqlify.type_system.TypeModel;
import org.aksw.sparqlify.type_system.TypeModelImpl;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.graph.Node;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.vocabulary.XSD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


public class TypeSystemImpl
	implements TypeSystem
{
	private static final Logger logger = LoggerFactory.getLogger(TypeSystemImpl.class);
	
	private TypeMapper typeMapper;

	private SqlTypeMapper sqlTypeMapper;
	
	private Map<String, SparqlFunction> nameToSparqlFunction = new HashMap<String, SparqlFunction>();
	private Map<String, SqlLiteralMapper> typeToLiteralMapper = new HashMap<String, SqlLiteralMapper>();

	// Maps database types (e.g. varchar and int4 to our schematic types, e.g. string and int)
	private IBiSetMultimap<TypeToken, TypeToken> physicalTypeMap = new BiHashMultimap<TypeToken, TypeToken>();
	
	private Map<String, SqlFunctionCollection> nameToSqlFunctions = new HashMap<String, SqlFunctionCollection>(); 
	
	private IBiSetMultimap<TypeToken, TypeToken> typeHierarchy = new BiHashMultimap<TypeToken, TypeToken>();
	private DirectSuperTypeProvider<TypeToken> typeHierarchyProvider = new TypeHierarchyProviderImpl(typeHierarchy);
	
	//private CoercionSystem<TypeToken, NodeValueTransformer> coercionSystem = new CoercionSystemImpl2(this); 
	private CoercionSystem<TypeToken, SqlValueTransformer> coercionSystem = new CoercionSystemImpl3(this);

	
	
	private IBiSetMultimap<String, String> sparqlTypeHierarchy = new BiHashMultimap<String, String>();
	private DirectSuperTypeProvider<String> sparqlTypeHierarchyProvider = new DirectSuperTypeProviderBiSetMultimap<String>(sparqlTypeHierarchy);

	private TypeModel<String> sparqlTypeModel = new TypeModelImpl<String>(sparqlTypeHierarchyProvider);

	
	// Maps a normalized sql type (e.g. boolean) to a corresponding url (e.g. xsd:boolean) on the SPARQL level
	private Map<String, String> normSqlTypeToUri = new HashMap<String, String>();
	
	public Map<String, String> getNormSqlTypeToUri() {
	    return normSqlTypeToUri;
	}
	
	/**
	 * Maps SPARQL functions to sets of declarations of SQL functions
	 * E.g. ogc:st_intersects -> {ST_INSERSECTS(geometry, geometry), ST_INTERSECTS(geography, geography)}
	 * 
	 * 
	 */
	
	private FunctionModel<TypeToken> functionModel = new FunctionModelImpl<TypeToken>(typeHierarchyProvider);
	private Multimap<String, String> sparqlToSqlDecl = HashMultimap.create();
	private Map<String, SqlExprEvaluator> sqlToImpl = new HashMap<String, SqlExprEvaluator>();
	private FunctionModelMeta sqlFunctionMetaModel = new FunctionModelMeta();
	
	private FunctionModelAliased<String> sparqlFunctionModel = new FunctionModelAliased<String>(new FunctionModelImpl<String>(sparqlTypeHierarchyProvider));

	public IBiSetMultimap<String, String> getSparqlTypeHierarchy() {
		return sparqlTypeHierarchy;
	}
	
	public Multimap<String, String> getSparqlSqlDecls() {
		return sparqlToSqlDecl;
	}

	public FunctionModel<TypeToken> getSqlFunctionModel() {
		return functionModel;
	}
	
	public Map<String, SqlExprEvaluator> getSqlImpls() {
		return sqlToImpl;
	}
	
	public IBiSetMultimap<TypeToken, TypeToken> getPhysicalTypeMap() {
		return physicalTypeMap;
	}
	
	@Override
	public FunctionModelMeta getSqlFunctionMetaModel() {
		return sqlFunctionMetaModel;
	}

//	
//	
//	
//	public void registerSparqlSqlImpl(String sparqlFnId, String sqlId) {
//		this.sparqlToSqlImpl.put(sparqlFnId, sqlId);
//	}
	


//	public Collection<String> getSqlImpls(String sparqlFnId) {
//		Collection<String> sqlFnIds = this.sparqlToSqlImpl.get(sparqlFnId);
//		return sqlFnIds;
//	}

//	public Coll
	

	public TypeSystemImpl() {
		// By default use Jena's default TypeMapper
		this.typeMapper = TypeMapper.getInstance();
		this.sqlTypeMapper = new SqlTypeMapperImpl();
		
	}

	public CoercionSystem<TypeToken, SqlValueTransformer> getCoercionSystem() {
		return coercionSystem;
	}
	
	public IBiSetMultimap<TypeToken, TypeToken> getTypeHierarchy() {
		return typeHierarchy;
	}
	
	
	@Override
	public void registerSparqlFunction(SparqlFunction sparqlFunction) {
		this.nameToSparqlFunction.put(sparqlFunction.getName(), sparqlFunction);
	}

	
	
	
	/**
	 * Registering the same name with different signatures (overloading)
	 * is allowed.
	 * 
	 */
	@Deprecated
	void registerSqlFunction(String name, SqlFunctionCollection sqlFunctions) {
		nameToSqlFunctions.put(name, sqlFunctions);
	}

	
	/**
	 * This mapper converts from NodeValues to SQL Literals and vice versa.
	 * 
	 */
	@Override
	public void registerLiteralMapper(String typeUri, SqlLiteralMapper mapper) {
		SqlLiteralMapper oldValue = typeToLiteralMapper.get(typeUri);
		if(oldValue != null) {
			throw new RuntimeException("Literal Mapper for type " + typeUri + " already registered. Redefinition with " + mapper);
		}
		
		typeToLiteralMapper.put(typeUri, mapper);		
	}

	@Override
	public TypeMapper getTypeMapper() {
		return typeMapper;
	}

	@Override
	@Deprecated
	public void registerCoercion(XMethod method) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SparqlFunction getSparqlFunction(String name) {
		SparqlFunction result = nameToSparqlFunction.get(name);
		return result;
	}

	
	/**
	 * Convert an RDF literal into a corresponding SQL literal
	 * 
	 */
	@Override
	public SqlValue convertSql(NodeValue value) //, TypeToken targetTypeToken)
	{
		if(value.hasNode()) {
			Node node = value.asNode();
			if(node.isURI()) {
				logger.warn("FIXME Replacing URI with string - this should be validated when loading views");
				value = NodeValue.makeString(node.getURI());
			}
		}
		
		if(!value.isLiteral()) {
			throw new RuntimeException("Only literals allowed here, got: " + value);
		}
		
		String datatypeUri = value.asNode().getLiteralDatatypeURI();
		if(datatypeUri == null) {
			datatypeUri = XSD.xstring.toString();
		}

		SqlDatatype sqlType = sqlTypeMapper.getSqlDatatype(datatypeUri);

		if(sqlType == null) {
			
			// NOTE sqlTypeMapper is configured in NewWorldTest.initSparqlModel
			
			throw new RuntimeException("No SQL conversion found for NodeValue: " + value + " ( " + datatypeUri + ")");
			
		}
		
		SqlValue result = sqlType.toSqlValue(value);
		return result;
	}

	@Override
	public SqlValue cast(SqlValue value, TypeToken targetTypeToken)
	{
		TypeToken sourceTypeToken = value.getTypeToken();
		//TypeToken targetTypeToken = TypeToken.alloc(targetTypeName);
		
		SqlValueTransformer transformer = coercionSystem.lookup(sourceTypeToken, targetTypeToken);

		if(transformer == null) {
			
			logger.warn("No cast found for: " + value + " to " + targetTypeToken + " --- assuming type error");
			//throw new RuntimeException(
			return SqlValue.TYPE_ERROR;
			
		}
		
		//NodeValue result;
		SqlValue result;
		try {
			result = transformer.transform(value);
			//result = transformer.transform(value);
		} catch (CastException e) {
			result = null;
		}

		return result;
	}

//	@Override
	public NodeValue cast(NodeValue value, TypeToken targetTypeToken)
	{
		if(!value.isLiteral()) {
			throw new RuntimeException("Only literals allowed here, got: " + value);
		}
		
		String sourceTypeName = value.asNode().getLiteralDatatypeURI();
		TypeToken sourceTypeToken;
		if(sourceTypeName == null) {
			sourceTypeToken = TypeToken.String;
		} else {
			sourceTypeToken= TypeToken.alloc(sourceTypeName); 
		}


		
		
		//TypeToken targetTypeToken = TypeToken.alloc(targetTypeName);
		NodeValueTransformer transformer = null;
		//NodeValueTransformer transformer = coercionSystem.lookup(sourceTypeToken, targetTypeToken);

		if(transformer == null) {
			
			throw new RuntimeException("No cast found for: " + value + " ( " + sourceTypeToken + ") to " + targetTypeToken);
			
		}
		
		NodeValue result;
		try {
			result = transformer.transform(value);
		} catch (CastException e) {
			result = null;
		}

		return result;
	}

	//@Override
	public SqlValueTransformer lookupCast(TypeToken sourceTypeName, TypeToken targetTypeName) {
		SqlValueTransformer result = coercionSystem.lookup(sourceTypeName, targetTypeName);
		return result;
	}

	@Override
	public boolean isSuperClassOf(TypeToken a, TypeToken b) {
		boolean result = TypeHierarchyUtils.isSuperTypeOf(a, b, typeHierarchyProvider);
		return result;
	}

	@Override
	public XClass resolve(String typeName) {
		XClass result = new XClassImpl2(this, this);
		return result;
	}

	@Override
	public Collection<TypeToken> getDirectSuperTypes(TypeToken name) {
		Collection<TypeToken> result = typeHierarchyProvider.getDirectSuperTypes(name);
		return result;
	}

	@Override
	public UnaryOperator<SqlExpr> cast(TypeToken fromTypeUri, TypeToken toTypeUri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void registerSqlFunction(XMethod sqlFunction) {
		// TODO Auto-generated method stub
		
	}

	
	
	/**
	 * 
	 * TODO Move to appropriate place
	 * 
	 * @param map
	 * @return
	 */
	public static <K, V> IBiSetMultimap<K, V> toBidiMap(Map<K, V> map) {
		IBiSetMultimap<K, V> result = new BiHashMultimap<K, V>();

		for(Entry<K, V> entry : map.entrySet()) {
			result.put(entry.getKey(), entry.getValue());
		}
		
		return result;
	}
	
	public static IBiSetMultimap<TypeToken, TypeToken> createHierarchyMap(Map<String, String> typeHierarchy) {
		IBiSetMultimap<TypeToken, TypeToken> subToSuperType = new BiHashMultimap<TypeToken, TypeToken>();
		for(Entry<String, String> entry : typeHierarchy.entrySet()) {
			
			TypeToken subType = TypeToken.alloc(entry.getKey());
			TypeToken superType = TypeToken.alloc(entry.getValue());
			
			// We get an infinite recursion if the super types of a class contain a sub class 
			if(superType.equals(subType)) {
				logger.warn("Skipping: " + subType + ", " + superType);
				continue;
			}

			
			//XClass subType = nameToType.get(entry.getKey());
			//XClass superType = nameToType.get(entry.getValue());

			subToSuperType.put(subType, superType);
		}
		
		return subToSuperType;
	}
	
	public static TypeSystemImpl create(Map<String, String> typeHierarchy, Map<String, String> rawPhysicalTypeMap) {
		
		IBiSetMultimap<TypeToken, TypeToken> subToSuperType = createHierarchyMap(typeHierarchy);

		IBiSetMultimap<TypeToken, TypeToken> physicalTypeMap = createHierarchyMap(rawPhysicalTypeMap);
		
		TypeSystemImpl result = new TypeSystemImpl();
		
		result.getTypeHierarchy().putAll(subToSuperType);
		result.getPhysicalTypeMap().putAll(physicalTypeMap);
		
		return result;
	}

	@Override
	public Set<TypeToken> supremumDatatypes(TypeToken from, TypeToken to) {
		Set<TypeToken> result = MultiMaps.getCommonParent(typeHierarchy.asMap(), from, to);
		return result;
	}

	@Override
	public SqlTypeMapper getSqlTypeMapper() {
		return sqlTypeMapper;
	}
	
	

	@Override
	public FunctionModelAliased<String> getSparqlFunctionModel() {
		return sparqlFunctionModel;
	}

	
	public TypeModel<String> getSparqlTypeModel() {
		return sparqlTypeModel;
	}
	
	
}