package org.jboss.netty.example.http.websocketx.server;

import java.io.Serializable;

import com.google.gson.annotations.Expose;

public class FaceoffStarted implements Serializable {
	
	@Expose
	private Player playerA;
	@Expose
	private Player playerB;
	@Expose
	private Round round;
	
	public FaceoffStarted() {}
	public FaceoffStarted(Player a, Player b, Round round) {
		playerA = a;
		playerB = b;
		this.round = round;
	}
	
	public void setPlayerA(Player p) {
		this.playerA = p;
	}
	
	public void setPlayerB(Player p) {
		this.playerB = p;
	}
	
	public Player getPlayerA() {
		return playerA;
	}
	
	public Player getPlayerB() {
		return playerB;
	}
	
	public void setRound(Round r) {
		round = r;
	}
	
	public Round getRound() {
		return round;
	}
	
}