package org.aksw.sparqlify.core.algorithms;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aksw.commons.util.reflect.MultiMethod;
import org.aksw.sparqlify.algebra.sql.nodes.Projection;
import org.aksw.sparqlify.algebra.sql.nodes.SqlNodeEmpty;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOp;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOpJoin;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOpQuery;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOpSelectBlock;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOpTable;
import org.aksw.sparqlify.algebra.sql.nodes.SqlOpUnionN;
import org.aksw.sparqlify.algebra.sql.nodes.SqlSortCondition;
import org.aksw.sparqlify.algebra.sql.nodes.SqlUnion;
import org.aksw.sparqlify.core.interfaces.SqlExprSerializer;
import org.aksw.sparqlify.core.interfaces.SqlOpSerializer;
import org.openjena.atlas.io.IndentedWriter;

import com.google.common.base.Joiner;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.sdb.core.JoinType;
import com.hp.hpl.jena.sparql.expr.Expr;



public class SqlOpSerializerImpl
	implements SqlOpSerializer
{
	private static SqlExprSerializer exprSerializer; //new SqlExprSerializerMySql();
	//private static SqlExprSerializer sqlExprSerializer = new SqlExprSerializerPostgres();
	
	
	public SqlOpSerializerImpl(SqlExprSerializer exprSerializer) {
		this.exprSerializer = exprSerializer;
	}
	
	// TODO The castFactory should most likely be part of the exprSerializer - we do not have to cast algebra ops.
	//private static DatatypeToStringPostgres castFactory = new DatatypeToStringPostgres();
	
	public String serialize(SqlOp op) {
		
		//SqlAlgebraToString transformer = new SqlAlgebraToString();
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		IndentedWriter writer = new IndentedWriter(out);
				
		serialize(op, writer);
		
		return out.toString();
	}
	
	public void serialize(SqlOp op, IndentedWriter writer) {
		
		
		//return (String)
		MultiMethod.invoke(this, "_serialize", op, writer);
	}
	
	/*
	public static String projection(Map<Var, Expr> map)
	{
    	String result = "";

    	List<String> strs = new ArrayList<String>();
    	for(Entry<Var, Expr> entry : map.entrySet()) {
    		
    		String keyStr = "";
    		String exprStr = "";
    		if(entry.getValue() != null) {
    			SqlExpr sqlExpr = SqlExprTranslator.translateMM(entry.getValue());
    			exprStr = sqlExpr.asSQL() + " ";

    			SqlExpr sqlKey = SqlExprTranslator.translateVar(entry.getKey());
    			keyStr = sqlKey.asSQL();
    		}

    		strs.add(exprStr + keyStr);
    	}

    	result = Joiner.on(", ").join(strs);
    	return result;
	}
	*/
	
	public static String projection(Projection projection) {
		String result = projection(projection.getNames(), projection.getNameToExpr());
		
		return result;
	}
	
	public static String projection(List<String> columnNames, Map<String, Expr> map)
	{
		// Empty projections can occur if a query response is determined by static triples
		if(map.isEmpty()) {
			return "true";
		}
		
    	String result = "";

    	List<String> strs = new ArrayList<String>();
    	
    	// When writing the projection as an SQL string, the column names will be sorted
    	// in order to make sure that the projections within unions are correctly aligned.
    	//SortedSet<String> columnNames = new TreeSet<String>(map.keySet());
    	for(String columnName : columnNames) {
    	//for(Entry<String, SqlExpr> entry : map.entrySet()) {
    		
    		Expr value = map.get(columnName);
    		//String keyStr = entry.getKey();
    		String exprStr = "";
    		
    		
    		if(value != null) {
    			exprStr = exprSerializer.serialize(value); //sqlExpr.asSQL() + " ";
    		} 
    		
    		strs.add(exprStr + " " + escapeAlias(columnName));
    	}

    	result = Joiner.on(", ").join(strs);
    	return result;
	}
	
	/**
	 * Column names that clash with keywords need to be escaped.
	 * TODO Make this properly. Also, maybe we need to do a renaming earlier.
	 * 
	 * @param columnName
	 * @return
	 */
	public static String escapeAlias(String columnName)
	{
		return "\"" + columnName + "\"";
	}
	
	
	
	
	
	
	
	
	
	
	public void _serialize(SqlNodeEmpty node, IndentedWriter writer) {
		writer.print("EMPTY_SQL_NODE");
	}

		
	public void _serialize(SqlOpQuery node, IndentedWriter writer)
	{
		// FIXME: Actually the parent node must determine whether to put the expression into parenthesis
		//String result;
		if(node.getAliasName() == null) {
			writer.print(node.getQueryString());
			
		} else {
			writer.print("(" + node.getQueryString() + ") " + node.getAliasName());			
		}
		
		//String result = "(SELECT " + projection(node.getColumnToSqlExpr()) + " FROM (" + node.getQueryString() + ") " + node.getInnerAlias() + ")" + node.getAliasName();
		//return result;
	}
	
	
	
	
	public static String getAliasName(SqlOp op) {
		
		String aliasName = SqlOpSelectBlock.getAliasName(op);
		
		if(aliasName == null || aliasName.isEmpty()) {
			return "";
		} else {
			return " " + aliasName;
		}		
	}
	
    public void _serialize(SqlOpSelectBlock op, IndentedWriter writer)
    {    	
    	writer.print("SELECT ");
    	
    	// Distinct
    	//String distinctStr = "";
    	if(op.isDistinct()) {
    		writer.print("DISTINCT ");
    		//distinctStr += " DISTINCT";
    	}
    	
    	// Projection
    	writer.println(projection(op.getProjection()));
    	
    	writer.println("FROM");

    	boolean isUnion = op.getSubOp() instanceof SqlOpUnionN || op.getSubOp() instanceof SqlOpSelectBlock;
    	
    	if(isUnion) {
    		writer.print("(");
    	}

    	
    	writer.incIndent();
    	// Joins
    	
    	serialize(op.getSubOp(), writer);
    	writer.decIndent();
    
    	if(isUnion) {
    		writer.print(") " + getAliasName(op));
    	}
    	
    	if(!writer.atLineStart()) {
    		writer.println();
    	}
    	
    	/*
    	if(!joinStr.isEmpty()) {
    		joinStr = "FROM " + joinStr;
    	}
    	*/
    	
/*
    	if(node.getSubNode() instanceof SqlUnionN) {
    		joinStr = "(" + joinStr + ") " + node.getAliasName(); 
    	}
*/  	
    	
    	// Selection
    	//String selectionStr = "";
    	{
	    	List<String> strs = new ArrayList<String>();
	    	for(Expr expr : op.getConditions()) {
	    		String str = exprSerializer.serialize(expr);
	    		//String str = expr.asSQL();
	    		
	    		strs.add(str);
	    	}
	    	
	    	if(!strs.isEmpty()) {
	    		writer.print("WHERE ");
	    		//selectionStr += " WHERE ";
	    	}
	    	
	    	writer.println(Joiner.on(" AND ").join(strs));
	    	//selectionStr += Joiner.on(" AND ").join(strs);
    	}    	
    	
    	
		List<String> sortColumnExprStrs = new ArrayList<String>();
    	for(SqlSortCondition condition : op.getSortConditions()) {
    		String dirStr = null;
    		if(condition.getDirection() == Query.ORDER_ASCENDING) {
    			dirStr = "ASC";
    		} else if(condition.getDirection() == Query.ORDER_DESCENDING) {
    			dirStr = "DESC";
    		}
    		
    		
    		// TODO This is not working properly: If a sparql variable is made up
    		// from multiple sql columns, we need to settle for an ordering -
    		// right now we get: c1 OR c2 OR ... cn
    		String exprStr = exprSerializer.serialize(condition.getExpression());
    					
			if(dirStr != null) {
				//exprStr = dirStr + "(" + exprStr + ")";
				exprStr = exprStr + " " + dirStr;
			}
    					
    		sortColumnExprStrs.add(exprStr);
    	}
    	String orderStr = "";
    	if(!sortColumnExprStrs.isEmpty()) {
        	orderStr = "ORDER BY " + Joiner.on(", ").join(sortColumnExprStrs);
    		writer.println(orderStr);
    	}

    	
    	//String limitStr = "";
    	if(op.getLimit() != null) {
    		writer.println("LIMIT " + op.getLimit());
    		//limitStr = " LIMIT " + node.getLimit();
    	}
    	
    	
    	String offsetStr = "";
    	if(op.getOffset() != null) {
    		writer.println("OFFSET " + op.getOffset());
    		//offsetStr = " OFFSET " + node.getOffset();
    	}
    	


    	/*
		List<String> sortColumnExprStrs = new ArrayList<String>();
    	for(SqlSortCondition condition : node.getSortConditions()) {
    		String dirStr = null;
    		if(condition.getDirection() == Query.ORDER_ASCENDING) {
    			dirStr = "ASC";
    		} else if(condition.getDirection() == Query.ORDER_DESCENDING) {
    			dirStr = "DESC";
    		}
    		
    		for(Var var : condition.getExpression().getVarsMentioned()) {
    			for(Expr expr : node.getSparqlVarToExprs().asMap().get(var)) {
    				for(Var columnName : expr.getVarsMentioned()) {
    					SqlExpr sqlExpr = node.getAliasToColumn().get(columnName.getName());
    					
    					String exprStr = sqlExprSerializer.serialize(sqlExpr);
    					
    					if(dirStr != null) {
    						//exprStr = dirStr + "(" + exprStr + ")";
    						exprStr = exprStr + " " + dirStr;
    					}
    					
    					sortColumnExprStrs.add(exprStr);
    				}
    			}
    		}
    	}
    	String orderStr = "";
    	if(!sortColumnExprStrs.isEmpty()) {
        	orderStr = " ORDER BY " + Joiner.on(", ").join(sortColumnExprStrs);
    	}
    	*/


    	
    	//String result = "SELECT " + distinctStr + projectionStr + " FROM\n" + joinStr + selectionStr + orderStr + limitStr + offsetStr;
    	
    	//return result;
    }


    /*
    public static String _serialize(SqlDisjunction node) {

    	
    	String left =  serialize(node.getLeft());
    	String right = serialize(node.getRight());
    			
    	String result = left + " UNION " + right;
    	return result;
    }*/

    public void _serialize(SqlUnion node, IndentedWriter writer) {
    	throw new RuntimeException("SqlUnion is deprecated. Use SqlUnionN instead.");
    	/*
    	String left =  serialize(node.getLeft());
    	String right = serialize(node.getRight());
    			
    	String result = left + " UNION ALL " + right;
    	return result;
    	*/
    }
    
    public void _serialize(SqlOpUnionN op, IndentedWriter writer) {
    	//writer.println("(");
		writer.incIndent();
    	
    	List<String> parts = new ArrayList<String>();
    	
    	List<SqlOp> members = op.getSubOps();
    	for(int i = 0; i < members.size(); ++i) {
    		SqlOp arg = members.get(i);
    	//for(SqlNode arg : node.getArgs()) {
    		
    		//String part = "SELECT " + projection(arg.getColumnToSqlExpr()) + " FROM " + serialize(arg) + " " + arg.getAliasName() + "";
    		//String sub = serialize(arg);
    		writer.incIndent();
    		serialize(arg, writer);
    		writer.decIndent();
    		
    		if(i != op.getSubOps().size() - 1) {
    			writer.println("UNION ALL");
    		}
    		
    		/*
    		if(arg instanceof SqlOpQuery) {
    			String innerAlias = ((SqlOpQuery) arg).getInnerAlias();
    			sub += " " + innerAlias;
    		}*/
    		
    		//String part = "SELECT " + projection(arg.getColumnToSqlExpr()) + " FROM " + sub;
    		//String part = sub;
    		
    		//parts.add(part + " ");
    	}
    	
    	writer.decIndent();
    	//writer.print(") " + node.getAliasName());
    	
    	//String result = "(" + Joiner.on(" UNION ALL ").join(parts) + ") " + node.getAliasName();
    	//return result;
    }

    /*
    public static String _serialize(SqlOpJoinN node) {
    	List<String> parts = new ArrayList<String>();
    	for(SqlNode arg : node.getArgs()) {
    		parts.add(serialize(arg));
    	}
    	
    	String result = Joiner.on(" JOIN ").join(parts);
    	return result;
    }*/
    
    
    public void serializeJoinU(SqlOp op, String aliasName, IndentedWriter writer) {
    	serialize(op, writer);


    	/*
    	boolean isSubSelect = node instanceof SqlSelectBlock || node instanceof SqlUnionN;
    	
    	if(isSubSelect) {
    		writer.print("(");
    		writer.incIndent();
    	}

    	serialize(node, writer);

    	if(isSubSelect) {
    		writer.decIndent();    		
    		if(!writer.atLineStart()) {
    			writer.println();
    		}
			writer.print(")" + aliasName);
    	} */
    }

    

    public void _serialize(SqlOpJoin op, IndentedWriter writer) {
    	//throw new RuntimeException("SqlUnion is deprecated. Use SqlUnionN instead.");

    	//writer.print("(");
   
    	serializeJoinU(op.getLeft(), SqlOpSelectBlock.getAliasName(op.getLeft()), writer);
    	//serializeJoinU(node.getLeft(), node.getLeftAlias(), writer);
    	
    	//writer.print(") AS " + node.getLeft().getAliasName());
    	
    	String restrictionStr = "";
    	List<String> strs = new ArrayList<String>();
    	for(Expr expr : op.getConditions()) {
    		strs.add(exprSerializer.serialize(expr));
    	}
    	restrictionStr = Joiner.on(" AND ").join(strs);
    	
    	if(!restrictionStr.isEmpty()) {
    		restrictionStr = " ON (" + restrictionStr + ")";
    	} else {
    		//restrictionStr = " ON (TRUE)";
    		restrictionStr = "";
    	}

    	
    	String joinOp = "";
    	
    	if(op.getJoinType().equals(JoinType.INNER)) {
        	if(strs.isEmpty()) {
        		joinOp = ",";
        		writer.println(joinOp);
        	} else {
        		joinOp = strs.isEmpty() ? ", " : "JOIN ";
        		writer.println();
            	writer.print(joinOp);
        	}
    	} else if(op.getJoinType().equals(JoinType.LEFT)) {
    		joinOp = "LEFT JOIN ";
    		writer.println();
        	writer.print(joinOp);
    	} else {
    		throw new RuntimeException("Join type not supported");
    	}

    	
    	
    	//writer.print("(");
    	
    	serializeJoinU(op.getRight(), SqlOpSelectBlock.getAliasName(op.getRight()), writer);
    	//serializeJoinU(node.getRight(), node.getRightAlias(), writer);
    	
    	//writer.print(") AS " + node.getRight().getAliasName());
    	
    	if(!restrictionStr.isEmpty()) {
    		writer.println(restrictionStr);
    	}
    	
    	
    	//writer.print(") AS " + node.getAliasName());

    	
    	//String result = left + " " + node.getLeft().getAliasName() + " JOIN " + right + " " + node.getRight().getAliasName() + restrictionStr;
    	//String result = left + joinOp + right + restrictionStr;
    	//String result = left + joinOp + right + restrictionStr;
    	//return result;
    }

    public static void _serialize(SqlOpTable op, IndentedWriter writer)
    {
    	writer.print(op.getTableName());
    	writer.print(getAliasName(op));
    }

    /*
	@Override
	public String serialize(SqlOp op) {
		// TODO Auto-generated method stub
		return null;
	}*/
}