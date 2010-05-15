package edu.ucla.cens.awserver.dao;

import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.log4j.Logger;
import org.springframework.jdbc.core.RowMapper;

import edu.ucla.cens.awserver.domain.MultiUserPromptGroupCountQueryResult;
import edu.ucla.cens.awserver.request.AwRequest;

/**
 * @author selsky
 */
public class MultiUserPromptGroupCountQueryDao extends AbstractDao {
	private static Logger _logger = Logger.getLogger(MultiUserPromptGroupCountQueryDao.class);
	
	private String _sql = "select count(*), campaign_prompt_group_id, time_stamp, login_id" +
			              " from prompt_response, prompt, user, campaign_prompt_group" +
	                      " where prompt_response.prompt_id = prompt.id" +
	                      " and campaign_id = ?" +
	                      " and prompt.campaign_prompt_group_id = campaign_prompt_group.id" +
	                      " and time_stamp between ? and ?" +
	                      " and user.id = user_id" +
	                      " group by user_id, campaign_prompt_group_id, date(time_stamp)";

	public MultiUserPromptGroupCountQueryDao(DataSource dataSource) {
		super(dataSource);
	}
	
	@Override
	public void execute(AwRequest awRequest) {
		String s = null, e = null;
		int c = -1;
		
		try {
			
			s = awRequest.getStartDate();
			e = awRequest.getEndDate();
			c = Integer.parseInt(awRequest.getUser().getCurrentCampaignId());

			awRequest.setResultList(
				getJdbcTemplate().query(_sql, new Object[] {c, s, e}, new RowMapper() {
					public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
						MultiUserPromptGroupCountQueryResult result = new MultiUserPromptGroupCountQueryResult();
						result.setCount(rs.getInt(1));
						result.setCampaignPromptGroupId(rs.getInt(2));
						result.setDate(rs.getDate(3).toString());
						result.setUser(rs.getString(4));
						return result;
					}
				})
			);
			
		} catch (org.springframework.dao.DataAccessException dae) {
			
			_logger.error(dae.getMessage() + " SQL: '" + _sql + "' Params: " + c + ", " + s + ", " + e);
			throw new DataAccessException(dae.getMessage());
			
		}
	}
}
