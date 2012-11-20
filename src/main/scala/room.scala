package acro

import java.util.UUID

import scala.collection.JavaConverters._
import java.util.concurrent.TimeUnit
import org.jboss.netty.util. {
	TimerTask, Timeout
}
import org.jboss.netty.example.http.websocketx.server._

import Handler._

object Timer {
	val underlying = new org.jboss.netty.util.HashedWheelTimer
	def apply[U](delay: Long, unit: TimeUnit)(f: => U) = {
		underlying.newTimeout(new TimerTask {
			def run(timeout: Timeout) {
				f
			}
		}, delay, unit)
	}
	def seconds[U](delay: Long) = Timer(delay, TimeUnit.SECONDS) _
}
case class Disconnected(userId: String)

class RoomActor(name: String) extends scala.actors.Actor {
	self => def answerTime = 60
	def voteTime = 45
	def gameOverSeconds = 30
	def newRoundTime = 10
	val room = new Room()
	room.setName(name)
	room.setAdult(false)
	room.setAnswerTime(answerTime)
	room.setId(UUID.randomUUID().toString())
	room.setVoteTime(voteTime)
	room.setGameOverSeconds(gameOverSeconds)
	room.setNewRoundTime(newRoundTime)
	def players = room.getPlayers.asScala

	def cleanup: Unit = {
		for (player <- players)
		if (!player.getContext.getChannel.isConnected) self!Disconnected(player.getUserId)
		Timer.seconds(1)(cleanup)
	}
	Timer.seconds(5)(cleanup)

	var rounds = List.empty[Round]
	var faceoffRounds = List.empty[Round]

	def act = loop {
		react {
		case Join(con) =>
			if (!room.isFull()) {
				room.join(con.channelContext, con.request)
			}
			con.write(gsonHeavy.toJson(new Response("jr", room)))
			val joinedRoom = Handler.gsonHeavy.toJson(new Response("nu", room.getPlayer(con.request.getUserId)))
			broadcast(joinedRoom)
			println(room.getState)
			if (room.getState == Room.State.CHATTING) {
				startRound()
			}
		case Message(con) => con.request.remove("type")
			con.request.remove("room")
			broadcast(gsonHeavy.toJson(
			new Response("m", con.request.getMessage())))
		case Answer(con) if room.getState == Room.State.WRITING_ACRONYMS || room.getState == Room.State.FACE_OFF =>
			if (faceoffRounds.isEmpty) {
				println("\n\nadding round answer\n\n")
				var pos = 0
				var good = true;
				for(k <- con.request.optString("acronym").toUpperCase.split(" ")) {					
					println(k + " vs " + rounds.head.getAcronym.charAt(pos))
					if(k.charAt(0)!=rounds.head.getAcronym.charAt(pos)) {
						good = false
					}
					pos = pos +1;
				}
				println(good)
				if(good) {
					rounds.head.addAnswer(con.request.getUserId, new Acronym(room.getPlayer(con.request.getUserId), con.request.optString("acronym")))
					val answerCount = Handler.gsonHeavy.toJson(
					new Response("ac", rounds.head.getAcronyms.size))
					broadcast(answerCount)
				} 
			} else {
				println("\n\nadding faceoff answer\n\n");
				var pos = 0
				var good = true;
				for(k <- con.request.optString("acronym").toUpperCase.split(" ")) {
					println(k + " vs " + rounds.head.getAcronym.charAt(pos))
					if(k.charAt(0)!=rounds.head.getAcronym.charAt(pos)) {
						good = false
					}
					pos = pos +1;
				}
				println(good)
				if(good) {
					faceoffRounds.head.addAnswer(con.request.getUserId, new Acronym(room.getPlayer(con.request.getUserId), con.request.optString("acronym")))
					val answerCount = Handler.gsonHeavy.toJson(
					new Response("ac", faceoffRounds.head.getAcronyms.size))
					broadcast(answerCount)
				}
			}

		case Vote(con) => 
			if (faceoffRounds.isEmpty) {
				rounds.head.addVote(con.request.getUserId, con.request.optString("acronym"))
			} else {
				faceoffRounds.head.addVote(con.request.getUserId, con.request.optString("acronym"))
			}
		case Leave(con) => room.removePlayer(con.request.getUserId)
			val left = Handler.gsonHeavy.toJson(new Response("lv", con.request.getUserId))
			broadcast(left)
		case Disconnected(userId) => println("removing " + userId)
			room.removePlayer(userId)
			val left = Handler.gsonHeavy.toJson(new Response("lv", userId))
			broadcast(left)
		}
	}

	def broadcast(str: String) {
		//println("broadcasting: " + str)
		for (player <- room.getPlayers.asScala) {
			Handler.write(player.getContext, str)
		}
	}
	val rand = new scala.util.Random

	
	def restartGame() {
		println("\n\n\n\nretarting game\n\n\n")
		val jl = new java.util.ArrayList [Round] (faceoffRounds.size)
		faceoffRounds.foreach (jl.add (_))
		val text = Handler.gsonHeavy.toJson(new Response("go", new GameOver(jl)))
		println(text)
		broadcast(text)
		for (player <- room.getPlayers.asScala) {
			player.setTotalVoteCount(0)
			rounds = List.empty[Round]
			faceoffRounds = List.empty[Round]
		}
		//send winner messages
		val startRoundTask = Timer.seconds(gameOverSeconds) {
			startRound();
		}		
	}
	def startFaceOffRound() {
		println("\n\nface off round")
		if (faceoffRounds.size >= 1) {
			println("faceoff over")
			restartGame()
			return;
		}
		val leaders = room.getLeaders.asScala
		var count = 0;
		room.startFaceOff()
		val size = (faceoffRounds.size % 4) + 3
		val chars = "ABCDEFGHIJKLMNOPQRSTVW".toSeq
		val acro = rand.shuffle(chars).take(size).mkString
		faceoffRounds = new Round::faceoffRounds
		println("\nnew:")
		faceoffRounds.head.setCategory("general")
		faceoffRounds.head.setAcronym(acro)
		faceoffRounds.head.setRound(faceoffRounds.size)
		println("Faceoff for: " + leaders(0).getUsername + ", " + leaders(1).getUsername)
		leaders.zipWithIndex foreach {
		case (player, index) =>
			if (index < 2) {
				val text = Handler.gsonHeavy.toJson(new Response("fo", faceoffRounds.head))
				Handler.write(player.getContext, text)
			} else {
				val txt = Handler.gsonHeavy.toJson(new Response("for", new FaceoffStarted(leaders(0), leaders(1), faceoffRounds.head)))
				Handler.write(player.getContext, txt)
			}
		}
		println("Accepting answers")
		val answersTimeout = Timer.seconds(answerTime + 5) {
			println("Processing answers")
			if (!faceoffRounds.isEmpty) {
				if (faceoffRounds.head.getAnswers.getAnswers.isEmpty) {
					startFaceOffRound()
				} else {
					val answers = Handler.gsonLight.toJson(
					new Response("fas", rounds.head.getAnswers))
					leaders.zipWithIndex foreach {
					case (player, index) =>
						if (index > 1) {
							Handler.write(player.getContext, answers)
						}
					}
					room.startFaceoffVoting()
					println("Accepting votes")
					Timer.seconds(voteTime + 1) {
						println("Processing votes")
						val kanswers = faceoffRounds.head.getAnswers;
						println("there are " + kanswers.getAnswers.size + " answers")
						val answers = Handler.gsonLight.toJson(
						new Response("fvc", kanswers))
						val winner = room.getPlayer(kanswers.getWinner)
						if (winner != null) {
							println("winner " + winner.getUsername)
							//winner.setTotalVoteCount(winner.getTotalVoteCount + rounds.head.getAcronym.length)
						} else {
							println("no winners")
						}
						leaders.zipWithIndex foreach {
						case (player, index) =>
							if (index < 2) {

							} else {
								Handler.write(player.getContext, answers)
							}
						}
						Timer.seconds(10) {
							startFaceOffRound()
						}
					}
				}
			}
		}		
	}
	def startRound() {
	    println("startRound() called")
		val leaders = room.getLeaders.asScala
		if (leaders.head != null && leaders.head.getTotalVoteCount >= 30) {
			startFaceOffRound();
		} else if (!room.hasEnoughPlayers) {
		    println("not enough players chatting")
			room.startChatting()
		} else {
	    	println("enough players, starting")
			room.startRound()
			val size = (rounds.size % 5) + 3
			val chars = "ABCDEFGHIJKLMNOPQRSTVW".toSeq
			val acro = rand.shuffle(chars).take(size).mkString
			rounds = new Round::rounds
			println("\nnew:")
			for {
				leader <- room.getLeaders.asScala
			} {
				println(leader.getUsername + " " + leader.getTotalVoteCount)
			}
			rounds.head.setCategory("general")
			rounds.head.setAcronym(acro)
			rounds.head.setRound(rounds.size)
			val text = Handler.gsonHeavy.toJson(new Response("sr", rounds.head))
			broadcast(text)
			val answerTimeout = Timer.seconds(answerTime+1) {
				if (rounds.head.getAnswers.getAnswers.isEmpty) {
					println("answers are empty")
					startRound()
				} else {
					println("answers are not empty, sending as")
					val answers = Handler.gsonLight.toJson(
					new Response("as", rounds.head.getAnswers))
					broadcast(answers)
					room.startVoting()
					Timer.seconds(voteTime + 1) {
						val kanswers = rounds.head.getAnswers;
						val answers = Handler.gsonLight.toJson(
						new Response("vc", kanswers))
						val winner = room.getPlayer(kanswers.getWinner)
						if (winner != null) {
							println("winner bonus " + winner.getUsername)
							winner.setTotalVoteCount(winner.getTotalVoteCount + rounds.head.getAcronym.length)
						} else {
							println("no winners")
						}
						val speedBonus = room.getPlayer(kanswers.getSpeeder)
						if (speedBonus != null) {
							println("speed bonus " + speedBonus.getUsername)
							speedBonus.setTotalVoteCount(speedBonus.getTotalVoteCount + 2)
						} else {
							println("no speed bonus")
						}
						for {
							player <- players
							answer <- Option(rounds.head.getAnswer(player.getUserId))
						} {
							player.setTotalVoteCount(
							player.getTotalVoteCount + answer.getVoteCount)
						}
						broadcast(answers)
						Timer.seconds(newRoundTime+1) {
							startRound()
						}
					}
				}
			}
			println(answerTimeout.getClass.getName)
		}
	}
}
