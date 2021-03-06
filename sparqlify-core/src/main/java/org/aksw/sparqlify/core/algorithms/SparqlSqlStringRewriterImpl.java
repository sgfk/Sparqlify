package org.aksw.sparqlify.core.algorithms;

import org.aksw.sparqlify.algebra.sql.nodes.SqlOp;
import org.aksw.sparqlify.core.domain.input.SparqlSqlOpRewrite;
import org.aksw.sparqlify.core.domain.input.SparqlSqlStringRewrite;
import org.aksw.sparqlify.core.interfaces.SparqlSqlOpRewriter;
import org.aksw.sparqlify.core.interfaces.SparqlSqlStringRewriter;
import org.aksw.sparqlify.core.sql.algebra.serialization.SqlOpSerializer;
import org.apache.jena.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SparqlSqlStringRewriterImpl
	implements SparqlSqlStringRewriter
{
	private static final Logger logger = LoggerFactory.getLogger(SparqlSqlStringRewriterImpl.class);
	
	private SparqlSqlOpRewriter sparqlSqlOpRewriter;
	private SqlOpSerializer sqlOpSerializer;
	
	public SparqlSqlStringRewriterImpl(
			SparqlSqlOpRewriter sparqlSqlOpRewriter,
			SqlOpSerializer sqlOpSerializer)
	{
		this.sparqlSqlOpRewriter = sparqlSqlOpRewriter;
		this.sqlOpSerializer = sqlOpSerializer;
	}
	
	
	public SparqlSqlOpRewriter getSparqlSqlOpRewriter() {
		return sparqlSqlOpRewriter;
	}

	public SqlOpSerializer getSqlOpSerializer() {
		return sqlOpSerializer;
	}




	@Override
	public SparqlSqlStringRewrite rewrite(Query query) {

		SparqlSqlOpRewrite rewrite = sparqlSqlOpRewriter.rewrite(query);
		SqlOp sqlOp = rewrite.getSqlOp();
		
		String sqlQueryString = sqlOpSerializer.serialize(sqlOp);
		SparqlSqlStringRewrite result = new SparqlSqlStringRewrite(sqlQueryString, rewrite.isEmptyResult(), rewrite.getVarDefinition(), rewrite.getProjectionOrder());

		logger.info("Variable Definitions:\n" + rewrite.getVarDefinition().toPrettyString());

		logger.debug("Sql Query:\n" + result.getSqlQueryString());
		
		return result;
	}	
}
