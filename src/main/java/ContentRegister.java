import static mindustry.Vars.netServer;

import arc.Events;
import arc.func.Cons;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Timer;
import arc.util.CommandHandler.CommandRunner;
import arc.util.Threads;

import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration.ActionType;
import mindustry.net.Packets.KickReason;

import util.ALog;
import util.Players;
import util.Strings;
import data.TempData;
import manager.CommandsManager;
import data.PVars;


public class ContentRegister {
	public static void initFilters() {
		//filter for muted, rainbowed players, disabled chat, and tags
    	netServer.admins.addChatFilter((p, m) -> {
    		TempData data = TempData.get(p);

    		if (PVars.tchat) {
    			if (data.isMuted) {
	    			util.Players.err(p, "You're muted, you can't speak.");
					return null;	
    			}
				
    		} else if (!p.admin) {
    			p.sendMessage("[scarlet]The tchat is disabled, you can't speak!");
    			return null;
    		}

    		Log.info("@@<@: @>", data.rainbowed ? "RAINBOWED: " : data.spectate() ? "VANISHED: " : "", data.noColorTag, data.realName, m);
    		if (data.spectate()) Call.sendMessage("[coral][[]:[white] " + m);
    		else if (!PVars.tags || PVars.bubbleChat) Call.sendMessage(m, p.name, p);
    		else Call.sendMessage(Strings.format("@[coral][[@[coral]]:[white] @", data.tag, p.name.substring(data.tag.length()), m));
    		
    		return null;
    	});
    	
    	//filter for players in GodMode
    	netServer.admins.addActionFilter(a -> {
    		if (a.player != null && TempData.get(a.player).inGodmode) {
    			if (a.type == ActionType.placeBlock) Call.constructFinish(a.tile, a.block, a.unit, (byte) a.rotation, a.player.team(), a.config);
    			else if (a.type == ActionType.breakBlock) Call.deconstructFinish(a.tile, a.block, a.unit);
    		}
    		return true;
    	});
	}
	
	public static void initEvents() {
    	//clear VNW & RTV votes and disabled it on game over
        Events.on(EventType.GameOverEvent.class, e -> {
        	PVars.canVote = false;
        	TempData.setField(p -> p.votedVNW = false);
        	TempData.setField(p -> p.votedRTV = false);
        	PVars.rtvSession.cancel();
        	PVars.vnwSession.cancel();
        });
        
        Events.on(EventType.WorldLoadEvent.class, e -> PVars.canVote = true); //re-enabled votes
        
        Events.on(EventType.PlayerConnect.class, e -> 
        	Threads.daemon("ConnectCheck_Player-" + e.player.id, () -> {
        		String name = Strings.stripGlyphs(Strings.stripColors(e.player.name)).strip();
        		
        		//check if the nickname is empty without colors and emoji
	        	if (name.isBlank()) {
	        		e.player.kick(KickReason.nameEmpty);
	        		return;
	        	}
        		
	        	//check the nickname of this player
        		if (manager.BansManager.checkName(e.player, name)) return; 

	        	//check if player have a VPN
	        	if (util.AntiVpn.checkIP(e.player.ip())) {
	        		e.player.kick("[scarlet]Anti VPN is activated on this server! []Please deactivate your VPN to be able to connect to the server.");
	        		ALog.write("VPN", "VPN found on the player @ [@]", name, e.player.uuid());
	        		return;
	        	}
		        	
		        //prevent to duplicate nicknames
	        	if (TempData.count(d -> d.stripedName.equals(name)) != 0) e.player.kick(KickReason.nameInUse);	
        	})
        );
        
        Events.on(EventType.PlayerJoin.class, e -> {
        	TempData data = TempData.put(e.player); //add player in TempData
        	
        	//for me =)
        	if (data.isCreator) { 
        		if (PVars.niceWelcome) Call.sendMessage("[scarlet]\ue80f " + e.player.name + "[scarlet] has connected! \ue80f [lightgray](Everyone must say: Hello creator! XD)");
        		Call.infoMessage(e.player.con, "Welcome creator! =)");
        	}				
			//for leo =)
			if (data.leo) {
					Call.infoMessage(e.player.con, "Welcome Leo! =)");      
			}
			
			//for the Lord =)
			if (data.lord) {
					Call.infoMessage(e.player.con, "Welcome Bruh' our lord! =)");      
			}
			Call.infoMessage(e.player.con, "[gold]Be sure to join the [blue]discord server!  https://discord.gg/5rPNwdz25f\n[red](Or else)");
			
        	//unpause the game if one player is connected
/*        	if (PVars.autoPause && Groups.player.size() == 1) {
        		state.serverPaused = false;
        		Log.info("auto-pause: Game unpaused...");
        		Call.sendMessage("[scarlet][Server]:[] Game unpaused...");
        	}
*/        	
        	//fix the admin bug
        	if (e.player.getInfo().admin) e.player.admin = true;
        	
        	//mute the player if the player has already been muted
        	if (PVars.recentMutes.contains(e.player.uuid())) data.isMuted = true;
        	
        	//apply the tag of this player
        	data.applyTag();
        	data.resetName();
        });
        
/*        Events.on(EventType.PlayerLeave.class, e -> {
        	//pause the game if no one is connected
        	if (PVars.autoPause && Groups.player.size()-1 == 0) {
        		state.serverPaused = true;
        		Log.info("auto-pause: Game paused...");
        	}

        	e.player.name = TempData.remove(e.player).realName; //remove player in TempData
        });
*/        
        //fix /votekick bug 
        Events.on(EventType.PlayerChatEvent.class, e -> {
        	if (e.message.startsWith("/votekick ") && mindustry.net.Administration.Config.enableVotekick.bool() && Groups.player.size() > 2 && !e.player.isLocal()) {
        		Players target = Players.findByName(Strings.stripGlyphs(Strings.stripColors(e.message.substring(10))).strip());
        		
        		if (target.found && target.data.rainbowed) {
        			target.data.rainbowed = false;
        			target.player.name = e.message.substring(10);
        			arc.util.Timer.schedule(() -> target.data.rainbowed = true, 0.2f);
        		}
        	}
        });
        
        Events.on(EventType.PlayerBanEvent.class, e -> ALog.write("Ban", "@ [@] has been banned of the server", netServer.admins.getInfoOptional(e.uuid).lastName, e.uuid));
        
        //save the unit of the player for the godmode
        Events.on(EventType.UnitChangeEvent.class, e -> {
	        TempData data = TempData.get(e.player);
        	
	        if (data != null) {
		        if (data.inGodmode) {
	        		data.lastUnit.health = data.lastUnit.maxHealth;
	        		e.unit.health = Integer.MAX_VALUE;
	        	}
	        	data.lastUnit = e.unit;		
	        }
        });
	}
	
	public static CommandsRegister setHandler(CommandHandler handler) {
		Threads.daemon("CommandManagerWaiter-" + handler.getPrefix(), () -> {
			try {
				Thread.sleep(1000);
				CommandsManager.load(handler);
			} catch (InterruptedException e) { e.printStackTrace(); }
		});
		
		return new CommandsRegister(handler);
	}
	
	
	public static class CommandsRegister {
		public CommandHandler handler;
		
		private CommandsRegister(CommandHandler handler) {
			this.handler = handler;
		}
		
		public void add(String name, String params, String desc, boolean forAdmin, boolean inThread, CommandRunner<TempData> runner) {
			if (forAdmin) PVars.adminCommands.add(name); 
			
			this.handler.<Player>register(name, params, desc, (arg, player) -> {
				if (forAdmin && !Players.adminCheck(player)) return;
				
				if (inThread) Threads.daemon("ClientCommandRunner_" + player.toString(), () -> runner.accept(arg, TempData.get(player)));
				else Timer.schedule(() -> {
					try { runner.accept(arg, TempData.get(player)); } 
					catch (Exception e) {
						Log.err("Exception in Timer \"ClientCommandRunner_" + player.toString() + "\"");
						e.printStackTrace();
					}
				}, 0);
			});
			
		}
		
		public void add(String name, String desc, Cons<String[]> runner) { add(name, "", desc, runner); }
		public void add(String name, String params, String desc, Cons<String[]> runner) {
			this.handler.register(name, params, desc, (arg) -> 
				Timer.schedule(() -> {
					try { runner.get(arg); } 
					catch (Exception e) {
						Log.err("Exception in Timer \"ServerCommandRunner_Name-" + name + "\"");
						e.printStackTrace();
					}
				 }, 0)
			);
		}
	}
}
