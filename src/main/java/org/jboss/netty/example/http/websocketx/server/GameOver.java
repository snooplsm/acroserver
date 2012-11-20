package org.jboss.netty.example.http.websocketx.server;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.annotations.Expose;

public class GameOver implements Serializable {
	
	@Expose
	private Map<String,Integer> players;
	
	@Expose
	private String winnerUserId;
	@Expose
	private String winnerUsername;
	@Expose
	private String loserUserId;
	@Expose
	private String loserUsername;
	@Expose
	private boolean isTie;
	
	public GameOver(List<Round> rounds) {
		Map<String,Integer> m = new HashMap<String,Integer>();
		for(Round r : rounds) {
			Map<String,Acronym> acros = r.getAcronyms();
			for(Map.Entry<String,Acronym> e : acros.entrySet()) {
				Integer count = m.get(e.getKey());
				if(count==null) {
					count = 0;
				}
				count+= e.getValue().getVoteCount();
				m.put(e.getKey(),count);
			}
		}
		players = m;
		List<String> pla = new ArrayList<String>();
		List<Integer> prr = new ArrayList<Integer>();
		for(Map.Entry<String, Integer> e : players.entrySet()) {
			pla.add(e.getKey());
			prr.add(e.getValue());
		}
		
		Collections.sort(pla,new Comparator<String> () {

			@Override
			public int compare(String arg0, String arg1) {
				Integer a = players.get(arg0);
				Integer b = players.get(arg1);
				return a.compareTo(b);
			}
			
		});
		if(pla.isEmpty()) {
			isTie = true;
		} else {
			winnerUserId = pla.get(0);
			if(pla.size()==2) {
				loserUserId = pla.get(1);
				if(players.get(winnerUserId).compareTo(players.get(loserUserId))==0) {
					isTie = true;
				}
			}			
		}
	}
	
	public Map<String,Integer> getPlayers() {
		return players;
	}
	
	public void setPlayers(Map<String,Integer> players) {
		this.players = players;
	}

	public String getWinnerUserId() {
		return winnerUserId;
	}

	public void setWinnerUserId(String winnerUserId) {
		this.winnerUserId = winnerUserId;
	}

	public String getWinnerUsername() {
		return winnerUsername;
	}

	public void setWinnerUsername(String winnerUsername) {
		this.winnerUsername = winnerUsername;
	}

	public String getLoserUserId() {
		return loserUserId;
	}

	public void setLoserUserId(String loserUserId) {
		this.loserUserId = loserUserId;
	}

	public String getLoserUsername() {
		return loserUsername;
	}

	public void setLoserUsername(String loserUsername) {
		this.loserUsername = loserUsername;
	}

	public boolean isTie() {
		return isTie;
	}

	public void setTie(boolean isTie) {
		this.isTie = isTie;
	}
	
}