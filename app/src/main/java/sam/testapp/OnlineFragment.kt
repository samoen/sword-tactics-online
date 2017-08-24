package sam.testapp

import android.app.Fragment
import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Chronometer
import android.widget.Toast
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.fragment_online.*

class OnlineFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.fragment_online,container,false)

    val MAX_PLAYERS_MINUS_ONE = 2
    val ANIMATION_TIME = 300
    var playerNumber = 0
    var playerReadies : MutableMap<Int,String> = mutableMapOf()
    var playerNames : MutableMap<Int,String> = mutableMapOf()
    var playerTypes : MutableMap<Int,String> = mutableMapOf()
    var playerSpeeds : MutableMap<Int,String> = mutableMapOf()
    var playerPositions : MutableMap<Int,String> = mutableMapOf()
    var playerLocations : MutableMap<Int,String> = mutableMapOf()
    var playerLoots : MutableMap<Int,String> = mutableMapOf()
    var activeAbilityType: String? = ""
    var firstDatabaseRead = true
    var isHeroDead: MutableMap<Int,Boolean> = mutableMapOf()
    var activeSlot = "head"
    var cooldowns = mutableMapOf<String,Int>("head" to 0, "shoulders" to 0, "legs" to 0,"offhand" to 0,"mainhand" to 0, "wait" to 0)
    var mDatabase: DatabaseReference? = null
    var mProgressDialog: ProgressDialog? = null
    var myspd: Int? = 0
    var roomFull = false
    var exited = false
    var Loot: MutableMap<Int,String> = mutableMapOf()
    var myLootSlot = "head"
    var lockPlayers = false

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showProgressDialog()
        StartFB(CP().room)
        InitViews()
    }

    fun InitViews(){
        chronometer_time.base = SystemClock.elapsedRealtime()
        chronometer_time.onChronometerTickListener = object: Chronometer.OnChronometerTickListener {
            override fun onChronometerTick(chronometer: Chronometer?) {
                if(chronometer?.text == "00:10"){
                    Wait()
                }
            }
        }
        folding_tab_bar.onFoldingItemClickListener = object : FoldingTabBar.OnFoldingItemSelectedListener {
            override fun onFoldingItemSelected(item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.ftb_menu_head -> {
                        activeSlot = "head"
                        SelectAbility(activeSlot)
                    }
                    R.id.ftb_menu_shoulders -> {
                        activeSlot = "shoulders"
                        SelectAbility(activeSlot)
                    }
                    R.id.ftb_menu_legs -> {
                        activeSlot = "legs"
                        SelectAbility(activeSlot)
                    }
                    R.id.ftb_menu_offhand -> {
                        activeSlot = "offhand"
                        SelectAbility(activeSlot)
                    }
                    R.id.ftb_menu_mainhand -> {
                        activeSlot = "mainhand"
                        SelectAbility(activeSlot)
                    }
                    R.id.ftb_menu_wait -> {
                        activeSlot = "wait"
                        SelectAbility(activeSlot)
                    }
                }
                return false
            }
        }

        gridView_online.adapter = OnlineImageAdapter(activity)

        gridView_online.setOnItemClickListener(object: AdapterView.OnItemClickListener{
            override fun onItemClick(parent: AdapterView<*>?, _view: View?, position: Int, id: Long) {

                if(IA().activeMarkers.contains(CalculatePairFromPosition(position)) || activeSlot == "wait"){
                    if(cooldowns[activeSlot] == 0){
                        IA().RemoveMarkers()
                        FB("Player${playerNumber}Type",activeAbilityType?:"wait")
                        FB("Player${playerNumber}Position",position.toString())
                        FB("Player${playerNumber}Speed",myspd.toString())
                        FB("Player${playerNumber}Ready","READY")
                        ClickableButtons(false)
                    }else  Toast.makeText(context,"Cooldown ${cooldowns[activeSlot]}",Toast.LENGTH_SHORT).show()
                }
            }
        })

        button_backFromAdventure.setOnClickListener {
            val simpleAlert = AlertDialog.Builder(activity).create()
            simpleAlert.setTitle(getString(R.string.leave_battle))
            simpleAlert.setMessage(getString(R.string.want_to_leave_battle))
            simpleAlert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.leave_battle), object : DialogInterface.OnClickListener{
                override fun onClick(dialog: DialogInterface?, which: Int) {
                    LeaveFB()
                    fragmentManager.beginTransaction().replace(R.id.frameLayout_main, MainFragment()).commitAllowingStateLoss()
                }
            })
            simpleAlert.show()
        }
    }

    fun ActivateHero(pNum:Int,pos:Int?,type:String?){
        if(isHeroDead[pNum] != true){
            IA().ClearLastMiss()
            IA().notifyDataSetChanged()
            var illegalMovement = false
            for (i in 0..MAX_PLAYERS_MINUS_ONE){
                if (i != pNum){
                    if (IA().PlayersBoardPos[i] == pos && type == "move"){
                        illegalMovement = true
                        Toast.makeText(context,getString(R.string.move_blocked),Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (!illegalMovement){
                if (pNum != playerNumber){
                    if (type=="attack"){
                        if(pos == IA().PlayersBoardPos[playerNumber]){
                            LeaveFB()
                            isHeroDead[playerNumber] = true
                            val simpleAlert = AlertDialog.Builder(activity).create()
                            simpleAlert.setTitle(getString(R.string.struck_down))
                            simpleAlert.setMessage(getString(R.string.you_survive))
                            simpleAlert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), object : DialogInterface.OnClickListener{
                                override fun onClick(dialog: DialogInterface?, which: Int) {}
                            })
                            simpleAlert.show()

                            when(myLootSlot){
                                "head"->CP().equipped.head = IL()[0]
                                "shoulders"->CP().equipped.shoulders = IL()[1]
                                "legs"->CP().equipped.legs = IL()[2]
                                "offhand"->CP().equipped.offhand = IL()[3]
                                "mainhand"->CP().equipped.mainhand = IL()[4]
                            }
                            CP().items.removeAll { it.id == Loot[playerNumber]?.toInt() }
                            fragmentManager.beginTransaction().replace(R.id.frameLayout_main, MainFragment()).commit()
                        }else if(IA().PlayersBoardPos.containsValue(pos)){
                            val enemyKey = IA().PlayersBoardPos.filter{ it.value == pos }.keys.firstOrNull()
                            IA().PutLootBag(pos?:0,Loot[enemyKey]?.toInt()?:0)
                        }else if(!IA().PlayersBoardPos.containsValue(pos)){
                            IA().PutMissToPosition(pos?:0)
                        }
                    } else if (type == "move" && IA().PlayersBoardPos.containsKey(pNum)){
                        IA().PutEnemyToPosition(pos?:0,pNum)
                    }
                }else if (pNum == playerNumber){
                    val enemyKey = IA().PlayersBoardPos.filter{ it.value == pos }.keys.firstOrNull()
                    if (type == "attack"){
                        if(IA().PlayersBoardPos.containsValue(pos)){
                            isHeroDead[enemyKey?:0] = true
                            IA().PutHitToPosition(pos?:0)
                            IA().KillEnemy(enemyKey?:0)
                            IA().PutLootBag(pos?:0,Loot[enemyKey]?.toInt()?:0)
                        }else if(!IA().PlayersBoardPos.containsValue(pos)){
                            IA().PutMissToPosition(pos?:0)
                        }
                    }else if(type == "move"){
                        IA().PutHeroToPosition(pos?:0,pNum)
                        FB("Player${pNum}Location", IA().PlayersBoardPos[pNum].toString())
                        if (IA().lootBags.containsKey(pos)){
                            Toast.makeText(context,"Picked up a ${IL()[IA().lootBags[pos]?:0]}",Toast.LENGTH_LONG).show()
                            CP().items.add(IL()[IA().lootBags[pos]?:0])
                            IA().RemoveLootBag(pos?:0)
                        }
                        if (pos == IA().cavePos){
                            CP().room = "GameRoom2"
                            LeaveFB()
                            fragmentManager.beginTransaction().replace(R.id.frameLayout_main, OnlineFragment()).commitAllowingStateLoss()
                        }
                    }
                }
                IA().notifyDataSetChanged()
            }
        }
    }


    fun StartFB(room: String){
        mDatabase =  FirebaseDatabase.getInstance().getReference(room)
        mDatabase?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                if(this@OnlineFragment.activity != null){
                    for(i in 0..MAX_PLAYERS_MINUS_ONE){
                        playerReadies.put(i, dataSnapshot.child("Player${i}Ready").getValue(String::class.java).toString())
                        playerNames.put(i, dataSnapshot.child("Player${i}Name").getValue(String::class.java).toString())
                        playerPositions.put(i, dataSnapshot.child("Player${i}Position").getValue(String::class.java).toString())
                        playerSpeeds.put(i, dataSnapshot.child("Player${i}Speed").getValue(String::class.java).toString())
                        playerTypes.put(i, dataSnapshot.child("Player${i}Type").getValue(String::class.java).toString())
                        playerLocations.put(i, dataSnapshot.child("Player${i}Location").getValue(String::class.java).toString())
                        playerLoots.put(i, dataSnapshot.child("Player${i}Loot").getValue(String::class.java).toString())
                    }
                    if(firstDatabaseRead){
                        for(i in 0..MAX_PLAYERS_MINUS_ONE){
                            if (playerNames[i]=="NO_NAME"){
                                val startpos = 99-((i+1)*3)
                                playerNumber = i
                                FB("Player${i}Location", startpos.toString())
                                FB("Player${i}Ready","NOT_READY")
                                FB("Player${i}Loot",CP().CalculateMyLoot().toString())
                                FB("Player${i}Name",CP().name)
                                IA().PutHeroToPosition(startpos,i)
                                break
                            }else if (i == MAX_PLAYERS_MINUS_ONE){
                                roomFull = true
                                Toast.makeText(context,"Game Room 1 Full",Toast.LENGTH_SHORT).show()
                                fragmentManager.beginTransaction().replace(R.id.frameLayout_main, MainFragment()).commit()
                            }
                        }
                        for (i in playerLocations){
                            if (i.key != playerNumber && playerNames[i.key]!="NO_NAME"){
                                IA().PutEnemyToPosition(i.value.toInt(),i.key)
                            }
                        }
                        firstDatabaseRead = false
                        ClickableButtons(true)
                        hideProgressDialog()

                    }else if(!firstDatabaseRead){

                        val activePlayerNames = playerNames.filter { it.value != "NO_NAME" }
                        val activePlayerSpeeds = playerSpeeds.filter { activePlayerNames.containsKey(it.key) }
                        val speedSortedPlayerNumbers = activePlayerSpeeds.toList().sortedByDescending { (_, v) -> v }.toMap()

                        val mpos = playerPositions
                        val mtyp = playerTypes
                        Loot = playerLoots

                        if(playerReadies[playerNumber]=="READY"){
                            var otherPlayersReadyOrDone = true
                            for(v in 0..MAX_PLAYERS_MINUS_ONE){
                                if(v != playerNumber && playerReadies[v] != "done" && playerReadies[v] != "READY" && playerNames[v]!="NO_NAME"){
                                    otherPlayersReadyOrDone = false
                                }
                            }
                            if(otherPlayersReadyOrDone){
                                when(activeSlot){
                                    "head"-> cooldowns.put("head",CP().equipped.head?.ability?.cooldown?:0)
                                    "shoulders"->cooldowns.put("shoulders",CP().equipped.shoulders?.ability?.cooldown?:0)
                                    "legs"->cooldowns.put("legs",CP().equipped.legs?.ability?.cooldown?:0)
                                    "offhand"->cooldowns.put("offhand",CP().equipped.offhand?.ability?.cooldown?:0)
                                    "mainhand"->cooldowns.put("mainhand",CP().equipped.mainhand?.ability?.cooldown?:0)
                                }
                                DecrementAllCooldowns()
                                lockPlayers = true
                                var inc = 0
                                for (v in speedSortedPlayerNumbers){
                                    Handler().postDelayed( {
                                        if(this@OnlineFragment.activity != null){
                                            ActivateHero(v.key,mpos[v.key]?.toInt(),mtyp[v.key])
                                        }
                                    },ANIMATION_TIME*inc.toLong() )
                                    inc++
                                }
                                Handler().postDelayed({
                                    if(this@OnlineFragment.activity != null){
                                        FB("Player${playerNumber}Ready","done")
                                    }
                                },ANIMATION_TIME*activePlayerNames.size.toLong())
                            }

                        }else if (playerReadies[playerNumber]=="done"){
                            var result = true
                            for(v in 0..MAX_PLAYERS_MINUS_ONE){
                                if(v != playerNumber && playerReadies[v] != "done" && playerReadies[v]!="NOT_READY" && playerNames[v]!="NO_NAME"){
                                    result = false
                                }
                            }
                            if (result){
                                FB("Player${playerNumber}Ready","NOT_READY")
                                ClickableButtons(true)
                                IA().ClearLastMiss()
                                lockPlayers = false
                            }

                        }else if (playerReadies[playerNumber]=="NOT_READY"){
                            for (i in 0..MAX_PLAYERS_MINUS_ONE){
                                if(i != playerNumber && !lockPlayers){
                                    if(playerNames[i] == "NO_NAME" && IA().PlayersBoardPos.containsKey(i)){
                                        Toast.makeText(context,getString(R.string.left),Toast.LENGTH_SHORT).show()
                                        IA().RemoveEnemy(i)
                                    }
                                    if (playerNames[i] != "NO_NAME" && !IA().PlayersBoardPos.containsKey(i)){
                                        Toast.makeText(context,"${playerNames[i]} ${getString(R.string.joined)}",Toast.LENGTH_SHORT).show()
                                        IA().PutEnemyToPosition( playerLocations[i]?.toInt()?:0,i)
                                        Loot.put(i,playerLoots[i].toString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
            override fun onCancelled(databaseError: DatabaseError) {}
        })
    }
    fun LeaveFB(){
        FB("Player${playerNumber}Name","NO_NAME")
        FB("Player${playerNumber}Location","0")
        FB("Player${playerNumber}Type","NO_TYPE")
        FB("Player${playerNumber}Position","0")
        FB("Player${playerNumber}Speed","0")
        FB("Player${playerNumber}Ready","NO_READY")
        FB("Player${playerNumber}Loot","0")

        CP().room = "GameRoom1"

    }

    fun FB(key: String, value: String ){
        mDatabase?.child(key)?.setValue(value)
    }
    fun SelectAbility(slot: String){
        IA().RemoveMarkers()
        when(slot){
            "head"->{
                myspd = CP().equipped.head?.ability?.speed
                activeAbilityType = CP().equipped.head?.ability?.type
                IA().PlaceMarkers(CP().equipped.head?.ability?.relative_pairs?.toList(),playerNumber)
            }
            "shoulders"->{
                myspd = CP().equipped.shoulders?.ability?.speed
                activeAbilityType = CP().equipped.shoulders?.ability?.type
                IA().PlaceMarkers(CP().equipped.shoulders?.ability?.relative_pairs?.toList(),playerNumber)
            }
            "legs"->{
                myspd = CP().equipped.legs?.ability?.speed
                activeAbilityType = CP().equipped.legs?.ability?.type
                IA().PlaceMarkers(CP().equipped.legs?.ability?.relative_pairs,playerNumber)
            }
            "offhand"->{
                myspd = CP().equipped.offhand?.ability?.speed
                activeAbilityType = CP().equipped.offhand?.ability?.type
                IA().PlaceMarkers(CP().equipped.offhand?.ability?.relative_pairs,playerNumber)
            }
            "mainhand"->{
                myspd = CP().equipped.mainhand?.ability?.speed
                activeAbilityType = CP().equipped.mainhand?.ability?.type
                IA().PlaceMarkers(CP().equipped.mainhand?.ability?.relative_pairs,playerNumber)
            }
            "wait"->{
                myspd = 0
                activeAbilityType = "wait"

            }
        }
    }
    fun Wait(){
        activeSlot = "wait"
        IA().RemoveMarkers()
        FB("Player${playerNumber}Type","wait")
        FB("Player${playerNumber}Ready","READY")
        ClickableButtons(false)
    }
    fun DecrementAllCooldowns(){
        for(v in cooldowns){
            val old = v.value
            if(old>0) cooldowns.put(v.key,old-1)
        }
    }
    fun showProgressDialog() {
        if (mProgressDialog == null) {
            mProgressDialog = ProgressDialog(context)
            mProgressDialog!!.setMessage(getString(R.string.list_loading))
            mProgressDialog!!.isIndeterminate = true
        }
        mProgressDialog!!.show()
    }

    fun hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog!!.isShowing) {
            mProgressDialog!!.dismiss()
        }
    }
    fun ClickableButtons(clickable:Boolean){
        if(clickable){
            Handler().postDelayed({
                folding_tab_bar.expand()
                ShowCooldownTextViews(false)
            },100)
            chronometer_time.base = SystemClock.elapsedRealtime()
            chronometer_time.start()
        }else if(!clickable){
            Handler().postDelayed({
                folding_tab_bar.rollUp()
                ShowCooldownTextViews(true)
            },100)
            chronometer_time.stop()
        }
    }
    fun ShowCooldownTextViews(showBlank:Boolean){
        if (showBlank){
            textView_offhand.setText("")
            textView_head.setText("")
            textView_legs.setText("")
            textView_mainhand.setText("")
            textView_shoulders.setText("")
        }else if (!showBlank){
            if(cooldowns["head"]!=0){ textView_head.setText("${cooldowns["head"]}") }else textView_head.setText("")
            if(cooldowns["shoulders"]!=0){ textView_shoulders.setText("${cooldowns["shoulders"]}") }else textView_shoulders.setText("")
            if(cooldowns["legs"]!=0){ textView_legs.setText("${cooldowns["legs"]}") }else textView_legs.setText("")
            if(cooldowns["offhand"]!=0){ textView_offhand.setText("${cooldowns["offhand"]}") }else textView_offhand.setText("")
            if(cooldowns["mainhand"]!=0){ textView_mainhand.setText("${cooldowns["mainhand"]}") }else textView_mainhand.setText("")
        }
    }


    override fun onStop() {
        super.onStop()
        if (!roomFull){
            LeaveFB()
        }
        exited = true
    }

    override fun onResume() {
        super.onResume()
        if (exited){
            fragmentManager.beginTransaction().replace(R.id.frameLayout_main, MainFragment()).commit()
        }
    }


    fun CP() = (activity as MainActivity).currentPlayer
    fun IA() = (gridView_online.adapter as OnlineImageAdapter)
    fun IL() = (activity as MainActivity).itemList
}