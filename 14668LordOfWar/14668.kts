@file:Depends("coreMindustry/utilNext", "调用菜单")
@file:Depends("coreMindustry/contentsTweaker", "修改核心单位,单位属性")

package mapScript

import arc.struct.ObjectIntMap
import arc.util.Time
import arc.util.Tmp
import mindustry.Vars.*
import mindustry.content.Blocks
import mindustry.content.Fx
import mindustry.core.World
import coreLibrary.lib.util.loop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import mindustry.Vars
import mindustry.content.Items
import mindustry.content.StatusEffects
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/**@author xkldklp
 * https://mdt.wayzer.top/v2/map/14668/latest
 */
name = "Lord of War"

val menu = contextScript<coreMindustry.UtilNext>()

val T1UnitCost by lazy { Vars.state.rules.tags.getInt("@T1UC", 8) }
val T1Units = arrayOf(
    UnitTypes.dagger,
    UnitTypes.nova,
    UnitTypes.merui,
    UnitTypes.elude,
    UnitTypes.stell
)
val T2UnitCost by lazy { Vars.state.rules.tags.getInt("@T2UC", 32) }
val T2Units = arrayOf(
    UnitTypes.pulsar,
    UnitTypes.poly,
    UnitTypes.atrax,
    UnitTypes.avert,
    UnitTypes.locus
)
val T3UnitCost by lazy { Vars.state.rules.tags.getInt("@T3UC", 128) }
val T3Units = arrayOf(
    UnitTypes.mace,
    UnitTypes.mega,
    UnitTypes.cleroi,
    UnitTypes.zenith,
    UnitTypes.precept
)
val T4UnitCost by lazy { Vars.state.rules.tags.getInt("@T4UC", 512) }
val T4Units = arrayOf(
    UnitTypes.spiroct,
    UnitTypes.cyerce,
    UnitTypes.anthicus,
    UnitTypes.antumbra,
    UnitTypes.vanquish
)
val T5UnitCost by lazy { Vars.state.rules.tags.getInt("@T5UC", 2048) }
val T5Units = arrayOf(
    UnitTypes.arkyid,
    UnitTypes.vela,
    UnitTypes.tecta,
    UnitTypes.sei,
    UnitTypes.scepter
)
val LordUnitCost by lazy { Vars.state.rules.tags.getInt("@LUC", 65536) }
val LordUnits = arrayOf(
    UnitTypes.toxopid,
    UnitTypes.aegires,
    UnitTypes.collaris,
    UnitTypes.eclipse,
    UnitTypes.conquer
)

fun UnitType?.cost(): Int{
    return when(this){
        in T1Units -> T1UnitCost
        in T2Units -> T2UnitCost
        in T3Units -> T3UnitCost
        in T4Units -> T4UnitCost
        in T5Units -> T5UnitCost
        in LordUnits -> LordUnitCost
        else -> 0
    }
}
fun UnitType?.levelUnits(): Array<UnitType>?{
    return when(this){
        in T1Units -> T1Units
        in T2Units -> T2Units
        in T3Units -> T3Units
        in T4Units -> T4Units
        in T5Units -> T5Units
        in LordUnits -> LordUnits
        else -> null
    }
}
fun Int.levelUnits(): Array<UnitType>?{
    return when(this){
        1 -> T1Units
        2 -> T2Units
        3 -> T3Units
        4 -> T4Units
        5 -> T5Units
        6 -> LordUnits
        else -> null
    }
}
fun UnitType?.level(): Int{
    return when(this){
        in T1Units -> 1
        in T2Units -> 2
        in T3Units -> 3
        in T4Units -> 4
        in T5Units -> 5
        in LordUnits -> 6
        else -> 0
    }
}

fun Float.format(i: Int = 2): String {
    return "%.${i}f".format(this)
}

val cityCoins: ObjectIntMap<CoreBuild> = ObjectIntMap()//城市的金钱
fun CoreBuild.coins(): Int { return cityCoins[this] }
fun CoreBuild.removeCoin(amount: Int) { cityCoins.put(this, coins() - amount) }
fun CoreBuild.addCoin(amount: Int) { cityCoins.put(this, coins() + amount)}
fun CoreBuild.setCoin(amount: Int) { cityCoins.put(this, amount) }

val cityLord: MutableMap<CoreBuild, String> = mutableMapOf()//城市的领主
fun CoreBuild.lord(): String? { return cityLord.getOrDefault(this, null) }
fun CoreBuild.lord(uuid: String): String? { return cityLord.put(this, uuid) }

val teamCoins: ObjectIntMap<Team> = ObjectIntMap()//城市的金钱
fun Team.coins(): Int { return teamCoins[this] }
fun Team.removeCoin(amount: Int) { teamCoins.put(this, coins() - amount) }
fun Team.addCoin(amount: Int) { teamCoins.put(this, coins() + amount)}
fun Team.setCoin(amount: Int) { teamCoins.put(this, amount) }

val playerInputing:MutableMap<String, Boolean> = mutableMapOf()
val playerLastSendText:MutableMap<String, String?> = mutableMapOf()

val playerCoins: ObjectIntMap<String> = ObjectIntMap()//玩家的金钱
fun Player.coins(): Int { return playerCoins[uuid()]}
fun Player.removeCoin(amount: Int) {
    playerCoins.put(uuid(), coins() - amount)
    sendMessage("[red]失去 $amount 金币")
}
fun Player.addCoin(amount: Int, quiet: Boolean = false) {
    playerCoins.put(uuid(), coins() + amount)
    if(!quiet) sendMessage("[green]得到 $amount 金币")
}
fun Player.setCoin(amount: Int) {
    playerCoins.put(uuid(), amount)
    sendMessage("[green]金币被设置为 $amount")
}

val playerUnitCap: ObjectIntMap<String> = ObjectIntMap()//玩家的军团单位上限
fun Player.unitCap(): Int{ return playerUnitCap[uuid()] }
fun Player.unitCap(unitCap: Int){ playerUnitCap.put(uuid(), unitCap)}

val unitOwner: MutableMap<mindustry.gen.Unit, String> = mutableMapOf()//单位的领主
fun mindustry.gen.Unit.owner(): String? { return unitOwner.getOrDefault(this, null) }

val playerUnit: MutableMap<String, UnitType?> = mutableMapOf()//玩家统领单位类型
fun Player.unitType(): UnitType?{ return playerUnit[uuid()] }
fun Player.unitType(unitType: UnitType){  playerUnit[uuid()] = unitType}

val playerLordUnit: MutableMap<String, UnitType?> = mutableMapOf()//玩家领主级单位类型
fun Player.lordUnitType(): UnitType?{ return playerLordUnit[uuid()] }
fun Player.lordUnitType(unitType: UnitType){  playerLordUnit[uuid()] = unitType}
val playerLordCooldown: ObjectIntMap<String> = ObjectIntMap()//玩家领主级单位召唤冷却
fun Player.checkLordCooldown(): Boolean{ return playerLordCooldown.get(uuid()) <= Time.timeSinceMillis(startTime) }
fun Player.setLordCooldown(time: Float){ playerLordCooldown.put(uuid(), (Time.timeSinceMillis(startTime) + time * 1000).toInt()) }

val cooldown: ObjectIntMap<String> = ObjectIntMap()//玩家收获城市资源冷却
val startTime by lazy { Time.millis() }
fun Player.checkCooldown(): Boolean{ return cooldown.get(uuid()) <= Time.timeSinceMillis(startTime) }
fun Player.setCooldown(time: Float){ cooldown.put(uuid(), (Time.timeSinceMillis(startTime) + time * 1000).toInt()) }

fun CoreBuild.level():Int {
    return when(block){
        Blocks.coreShard -> 1
        Blocks.coreFoundation -> 2
        Blocks.coreBastion -> 3
        Blocks.coreNucleus -> 4
        Blocks.coreCitadel -> 5
        Blocks.coreAcropolis -> 6
        else -> 0
    }
}
fun CoreBuild.levelText(): String {
    return when (level()) {
        1 -> "村庄"
        2 -> "乡镇"
        3 -> "庄园"
        4 -> "城市"
        5 -> "大城市"
        6 -> "首都"
        else -> ""
    }
}

fun Player.createUnit(core: CoreBuild,unitType: UnitType? = unitType(), team: Team = team()): Boolean{
    if (unitType == null || Groups.unit.filter { u -> u.owner() == uuid() }.size >= unitCap()) return false
    var times = 0
    val spawnRadius = 5
    val unit = unitType.create(team)
    while (true) {
        Tmp.v1.rnd(spawnRadius.toFloat() * tilesize)

        val sx = core.x + Tmp.v1.x
        val sy = core.y + Tmp.v1.y

        if (unit.canPass(World.toTile(sx), World.toTile(sy))) {
            unit.set(sx, sy)
            unitOwner[unit] = uuid()
            break
        }

        if (++times > 20) {
            return false
        }
    }
    unit.apply {
        unitOwner[this] = uuid()
        add()
    }
    return true
}
fun Player.createLordUnit(core: CoreBuild,unitType: UnitType? = lordUnitType()): Boolean{
    if (unitType == null) return false
    var times = 0
    val spawnRadius = 5
    val unit = unitType.create(team())
    unit.apply {
        while (true){
            Tmp.v1.rnd(spawnRadius.toFloat() * tilesize)

            val sx = core.x + Tmp.v1.x
            val sy = core.y + Tmp.v1.y

            if (canPass(World.toTile(sx), World.toTile(sy))) {
                set(sx, sy)
                break
            }

            if (++times > 20) {
                return false
            }
        }
        launch(Dispatchers.game){
            unitOwner[unit] = uuid()
            val spawnTime = Time.millis()
            while(Time.timeSinceMillis(spawnTime) / 1000 <= 120){
                Call.label("${unit.type.emoji()}[red]领主级单位降临[white]${unit.type.emoji()}\n$name [white]${120 - Time.timeSinceMillis(spawnTime) / 1000}", 0.2026f, x, y)
                Call.effect(Fx.spawnShockwave, x, y, 0f, team().color)
                delay(200)
            }
            spawnedByCore = true
            apply(StatusEffects.boss, Float.MAX_VALUE)
            apply(StatusEffects.overdrive, Float.MAX_VALUE)
            apply(StatusEffects.overclock, Float.MAX_VALUE)
            apply(StatusEffects.shielded, Float.MAX_VALUE)
            add()
            unit(unit)
            Call.announce("$name [#${team().color}]领主级单位\n!${unit.type.emoji()}出征${unit.type.emoji()}!")
            Call.logicExplosion(team, x, y, 32f * 8f, 6000f, true, true, true)
            Call.effect(Fx.impactReactorExplosion, x, y, 0f, team().color)
        }
    }
    return true
}

suspend fun Player.cityMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]城池页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]当前城市拥有金币[yellow]${core.coins()}
            [cyan]你当前拥有金币[yellow]${coins()}
            [cyan]升级城池将会变为此城池领主！会自动收集一半的金币
            [red]核心机收取资源将减少！
        """.trimIndent()
    ) {
        if(checkCooldown()){
            this += listOf("[green]收取资源" to {
                if(checkCooldown()) {
                    val amount =
                        (core.coins() * if (unit().type != UnitTypes.alpha) Random.nextFloat() else Random.nextFloat() / 4).toInt()
                    addCoin(amount)
                    core.removeCoin(amount)
                    setCooldown(
                        (amount / max(
                            Time.timeSinceMillis(startTime) / 1000 / 30,
                            1L
                        ) / core.level()).toFloat()
                    )
                    Call.transferItemEffect(Items.copper, core.x, core.y, unit())
                }
            })
        }
        else{
            this += listOf("[red]收取冷却！${cooldown[uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s Left!" to {
                cityMenu(core)
            })
        }
        if(core.level() < 6) {
            if (core.coins() >= core.maxHealth && core.block != Blocks.coreAcropolis) {
                this += listOf("[cyan]可升级城池！\n[white]${Iconc.blockCliff}${core.maxHealth}" to {
                    if (core.coins() >= core.maxHealth && core == (core.tile.build as CoreBuild)) {
                        val cost = core.maxHealth.toInt()
                        val coins = core.coins()
                        val tile = core.tile
                        val target = when (core.block) {
                            Blocks.coreShard -> Blocks.coreFoundation
                            Blocks.coreFoundation -> Blocks.coreBastion
                            Blocks.coreBastion -> Blocks.coreNucleus
                            Blocks.coreNucleus -> Blocks.coreCitadel
                            Blocks.coreCitadel -> Blocks.coreAcropolis
                            else -> Blocks.coreShard
                        }
                        tile.setNet(target, core.team, 0)
                        (tile.build as CoreBuild).setCoin(coins - cost)
                        (tile.build as CoreBuild).lord(uuid())
                        Call.sendMessage("[#${core.team.color}]位于[${World.toTile(core.x)},${World.toTile(core.y)}]的 ${core.block.emoji()} 已经被[white] $name [#${core.team.color}]升级为 ${tile.build.block.emoji()}[white]${(tile.build as CoreBuild).levelText()}")
                    }
                })
            } else {
                this += listOf("[lightgray]城池金币不足以升级城池！\n[white]${Iconc.blockCliff}${core.maxHealth}" to {
                    cityMenu(core)
                })
            }
        }
        this += listOf(
            "军团" to {warMenu(core)},
            "[green]城池" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
        if (admin && Groups.player.size() <= 5){
            this += listOf(
                "[red]<ADMIN>自己加100000金币" to { addCoin(100000) },
                "[red]<ADMIN>城池加100000金币" to { core.addCoin(100000) },
                "[red]<ADMIN>随机领主" to { if (lordUnitType() != null) lordUnitType(lordUnitType().levelUnits()!!.random()) }
            )
        }
    }
}
suspend fun Player.warMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]战争页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[yellow]${Iconc.blockCliff}${coins()}
            [cyan]你需要金币[yellow]${Iconc.blockCliff}${unitType().cost() * 25}[cyan]来升级军团等级
            [lightgray]当前军团等级:${unitType().level()}
        """.trimIndent()
    ) {
        if (unitType() == null){
            this += listOf(
                "[red]你还没有军团！\n点击抽取你的军团单位！" to {
                    val randomUnit = T1Units.random()
                    unitType(randomUnit)
                    Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                    unitCap(16)
                    warMenu(core)
                }
            )
        }else{
                this += listOf(
                    (if (coins() >= unitType().cost() * 5) "[red]军团不满意？\n" + "重新抽取军团单位！\n" + "[white]${Iconc.blockCliff}${unitType().cost() * 5}"
                    else "[lightgray]你需要[white]${Iconc.blockCliff}${unitType().cost() * 5}[lightgray]来重新抽取军团单位") to {
                        if (coins() >= unitType().cost() * 5) {
                            val randomUnit = unitType().levelUnits()!!.random()
                            unitType(randomUnit)
                            Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                            removeCoin(unitType().cost() * 5)
                            warMenu(core)
                        } else {
                            sendMessage("[red]金钱不足！")
                        }
                    },
                    if (coins() >= unitType().cost() * 25 && unitType().level() < 5)
                        "[red]军团可升级!\n[white]${Iconc.blockCliff}${unitType().cost() * 25}" to {
                            if (coins() >= unitType().cost() * 25 && unitType().level() < 5) {
                                removeCoin(unitType().cost() * 25)
                                val randomUnit = (unitType().level() + 1).levelUnits()!!.random()
                                unitType(randomUnit)
                                Call.announce(con, "[cyan]抽取新军团单位:[white]${randomUnit.emoji()}")
                                warMenu(core)
                            } else {
                                sendMessage("[red]金钱不足！")
                            }
                        } else if (unitType().level() < 5)
                        "[cyan]你需要金币[yellow]${Iconc.blockCliff}${unitType().cost() * 25}[cyan]来升级军团等级" to {
                            warMenu(core)
                        } else
                        "[cyan]军团等级已满！" to {
                            warMenu(core)
                        }
                )
            this += listOf(
                "${unitType()!!.emoji()}${Iconc.blockCliff}${unitType().cost()}${unitType()!!.emoji()}" to {
                    if (coins() >= unitType().cost()){
                        if (createUnit(core)){
                            removeCoin(unitType().cost())
                            warMenu(core)
                        }else{
                            sendMessage("[red]生成失败！")
                        }
                    }else{
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
            if (unitType().level() == 5){
                if (lordUnitType() == null) {
                    this += listOf(
                        "[red]领主级单位解锁！\n点击抽取你的领主级单位！" to {
                            val randomUnit = LordUnits.random()
                            lordUnitType(randomUnit)
                            Call.announce(con, "[cyan]抽取领主级单位:[white]${randomUnit.emoji()}")
                            Call.sendMessage("[white]$name [#${team().color}]抽取了领主级单位${randomUnit.emoji()}!")
                            warMenu(core)
                        }
                    )
                }
                if (checkLordCooldown() && lordUnitType() != null){
                    this += listOf(
                        "[red]领主级单位冷却完毕！\n点击出征！\n${lordUnitType().cost()}${lordUnitType()!!.emoji()}" to {
                            if (coins() >= lordUnitType().cost()){
                                if (createLordUnit(core)) {
                                    removeCoin(lordUnitType().cost())
                                    Call.sendMessage("$name [#${team().color}]领主级单位${lordUnitType()!!.emoji()}准备出征!")
                                    Call.announce(con, "[red]Tips退出领主级单位将直接消失！")
                                    setLordCooldown(900f)
                                    lordUnitType(lordUnitType().levelUnits()!!.random())
                                    warMenu(core)
                                } else {
                                    sendMessage("[red]生成失败！")
                                }
                            } else {
                                sendMessage("[red]金钱不足！")
                            }
                        }
                    )
                }
            }
        }

        this += listOf(
            "[green]军团" to {warMenu(core)},
            "城池" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
    }
}
suspend fun Player.bankMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]银行页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[yellow]${Iconc.blockCliff}${coins()}
            [cyan]银行当前拥有金币[yellow]${Iconc.blockCliff}${team().coins()}
            [cyan]银行金币可供所有队友存取！
        """.trimIndent()
    ) {
    playerInputing[uuid()] = false
    this += listOf(
            "存金币[yellow]${Iconc.blockCliff}" to {
                    val playerLastText = playerLastSendText[uuid()]
                    sendMessage("------------\n[yellow]请输入所存数量\n[white]------------")
                    val startInputTime = Time.millis()
                    var fail = true
                    var coin = 0
                    playerInputing[uuid()] = true
                    while (Time.timeSinceMillis(startInputTime) / 1000 <= 15) {
                        if (playerInputing[uuid()] == false) break
                        if (playerLastText != playerLastSendText[uuid()]){
                            val amount = playerLastSendText[uuid()]?.toIntOrNull()
                            if (amount == null || amount <= 0 || amount > coins()) break
                            team().addCoin(amount)
                            removeCoin(amount)
                            coin = amount
                            fail = false
                            break
                        }
                        yield()
                    }
                    playerLastSendText[uuid()] = ""
                    if (fail) {
                        sendMessage("存钱失败！")
                    } else {
                        Call.sendMessage("$name [#${team().color}]往队伍银行存储${Iconc.blockCliff}$coin")
                    }

            },
        "取金币[yellow]${Iconc.blockCliff}" to {
                if (team().coins() > 0) {
                    val playerLastText = playerLastSendText[uuid()]
                    sendMessage("------------\n[yellow]请输入所取数量\n[white]------------")
                    val startInputTime = Time.millis()
                    var fail = true
                    var coin = 0
                    playerInputing[uuid()] = true
                    while (Time.timeSinceMillis(startInputTime) / 1000 <= 15) {
                        if (playerInputing[uuid()] == false) break
                        if (playerLastText != playerLastSendText[uuid()]){
                            val amount = playerLastSendText[uuid()]?.toIntOrNull()
                            if (amount == null || amount > team().coins() || amount <= 0) break
                            team().removeCoin(amount)
                            addCoin(amount)
                            coin = amount
                            fail = false
                            break
                        }
                        yield()
                    }
                    playerLastSendText[uuid()] = ""
                    if (fail) {
                        sendMessage("取钱失败！")
                    } else {
                        Call.sendMessage("$name [#${team().color}]往队伍银行取出${Iconc.blockCliff}$coin")
                    }
                } else {
                    sendMessage("[red]队伍银行没钱给你取！")
                }
            }
    )
    this += listOf(
        "军团" to {warMenu(core)},
        "城池" to {cityMenu(core)},
        "[green]银行" to {bankMenu(core)},
        "领主" to {lordMenu(core)}
    )
    this += listOf(
        "取消" to {}
    )
    }
}

suspend fun Player.lordMenu(core: CoreBuild) {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "[green]领主页面\n[cyan]City-level[yellow]${core.level()}",
        """
            [cyan]你当前拥有金币[yellow]${Iconc.blockCliff}${coins()}
            [cyan]升级属性！
        """.trimIndent()
    ) {
        fun Float.getRulesCost(cost: Int): Float {
            return (this * cost).pow(2)
        }


        if (unitType().level() > 0) {
            this += listOf(
                "单位上限\n${unitCap()}->${unitCap() + 4}\n${Iconc.blockCliff}${unitCap() * unitCap()}" to {
                    if (coins() >= unitCap() * unitCap()) {
                        removeCoin(unitCap() * unitCap())
                        unitCap(unitCap() + 4)
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
            this += listOf(
                "建筑血量\n${team().rules().blockHealthMultiplier.format()}->${(team().rules().blockHealthMultiplier + 0.05f).format()}\n${Iconc.blockCliff}${team().rules().blockHealthMultiplier.getRulesCost(30).toInt()}" to {
                    if (coins() >= team().rules().blockHealthMultiplier.getRulesCost(40)) {
                        removeCoin(team().rules().blockHealthMultiplier.getRulesCost(40).toInt())
                        team().rules().blockHealthMultiplier += 0.05f
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑血量(${(team().rules().blockHealthMultiplier - 0.05f).format()} -> ${team().rules().blockHealthMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                },
                "建筑攻击\n${team().rules().blockDamageMultiplier.format()}->${(team().rules().blockDamageMultiplier + 0.05f).format()}\n${Iconc.blockCliff}${team().rules().blockDamageMultiplier.getRulesCost(30).toInt()}" to {
                    if (coins() >= team().rules().blockDamageMultiplier.getRulesCost(40)) {
                        removeCoin(team().rules().blockDamageMultiplier.getRulesCost(40).toInt())
                        team().rules().blockDamageMultiplier += 0.05f
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑攻击(${(team().rules().blockDamageMultiplier - 0.05f).format()} -> ${team().rules().blockDamageMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
            this += listOf(
                "建筑速度\n${team().rules().buildSpeedMultiplier.format()}->${(team().rules().buildSpeedMultiplier + 0.05f).format()}\n${Iconc.blockCliff}${team().rules().buildSpeedMultiplier.getRulesCost(30).toInt()}" to {
                    if (coins() >= team().rules().buildSpeedMultiplier.getRulesCost(25)) {
                        removeCoin(team().rules().buildSpeedMultiplier.getRulesCost(25).toInt())
                        team().rules().buildSpeedMultiplier += 0.05f
                        Call.sendMessage("[white]$name [#${team().color}]购买了建筑速度(${(team().rules().buildSpeedMultiplier - 0.05f).format()} -> ${team().rules().buildSpeedMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                },
                "单位攻击\n${team().rules().unitDamageMultiplier.format()}->${(team().rules().unitDamageMultiplier + 0.05f).format()}\n${Iconc.blockCliff}${team().rules().unitDamageMultiplier.getRulesCost(45).toInt()}" to {
                    if (coins() >= team().rules().unitDamageMultiplier.getRulesCost(45)) {
                        removeCoin(team().rules().unitDamageMultiplier.getRulesCost(45).toInt())
                        team().rules().unitDamageMultiplier += 0.05f
                        Call.sendMessage("[white]$name [#${team().color}]购买了单位攻击(${(team().rules().unitDamageMultiplier - 0.05f).format()} -> ${team().rules().unitDamageMultiplier.format()}}")
                        lordMenu(core)
                    } else {
                        sendMessage("[red]金钱不足！")
                    }
                }
            )
        }
        this += listOf(
            "军团" to {warMenu(core)},
            "城池" to {cityMenu(core)},
            "银行" to {bankMenu(core)},
            "[green]领主" to {lordMenu(core)}
        )
        this += listOf(
            "取消" to {}
        )
    }
}
onEnable{
    //contextScript<coreMindustry.ContentsTweaker>().addPatch("LordOfWar", dataDirectory.child("contents-patch").child("14668.json").readString())
    contextScript<coreMindustry.ContentsTweaker>().addPatch("Lord Of War",
           "{\n" +
                   "  \"block\": {\n" +
                   "    \"core-shard\": {\n" +
                   "      \"health\": 2000,\n" +
                   "      \"armor\": 10\n" +
                   "    },\n" +
                   "    \"core-foundation\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 4000,\n" +
                   "      \"armor\": 15\n" +
                   "    },\n" +
                   "    \"core-bastion\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 8000,\n" +
                   "      \"armor\": 20\n" +
                   "    },\n" +
                   "    \"core-nucleus\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 12000,\n" +
                   "      \"armor\": 25\n" +
                   "    },\n" +
                   "    \"core-citadel\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 32000,\n" +
                   "      \"armor\": 30\n" +
                   "    },\n" +
                   "    \"core-acropolis\": {\n" +
                   "      \"unitType\": \"alpha\",\n" +
                   "      \"health\": 80000,\n" +
                   "      \"armor\": 35\n" +
                   "    }\n" +
                   "  },\n" +
                   "  \"unit\": {\n" +
                   "    \"dagger\": {\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"nova\": {\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"merui\": {\n" +
                   "      \"health\": 120,\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"elude\": {\n" +
                   "      \"health\": 120,\n" +
                   "      \"weapons.0.bullet.damage\": 6,\n" +
                   "      \"armor\": 0\n" +
                   "    },\n" +
                   "    \"stell\": {\n" +
                   "      \"health\": 120,\n" +
                   "      \"weapons.0.bullet.damage\": 14,\n" +
                   "      \"armor\": 4\n" +
                   "    },\n" +
                   "    \"pulsar\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 9,\n" +
                   "      \"weapons.0.bullet.lightningLengthRand\": 0,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"poly\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"atrax\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 18,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"avert\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 14,\n" +
                   "      \"armor\": 3\n" +
                   "    },\n" +
                   "    \"locus\": {\n" +
                   "      \"health\": 360,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"armor\": 8\n" +
                   "    },\n" +
                   "    \"mace\": {\n" +
                   "      \"health\": 620,\n" +
                   "      \"weapons.0.bullet.damage\": 38,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"mega\": {\n" +
                   "      \"health\": 320,\n" +
                   "      \"weapons.0.bullet.damage\": 12,\n" +
                   "      \"weapons.2.bullet.damage\": 6,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"cleroi\": {\n" +
                   "      \"health\": 460,\n" +
                   "      \"weapons.2.bullet.damage\": 12,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"zenith\": {\n" +
                   "      \"health\": 420,\n" +
                   "      \"weapons.0.bullet.damage\": 32,\n" +
                   "      \"armor\": 6\n" +
                   "    },\n" +
                   "    \"precept\": {\n" +
                   "      \"health\": 840,\n" +
                   "      \"weapons.0.bullet.damage\": 36,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 20,\n" +
                   "      \"weapons.0.bullet.fragBullet.damage\": 12,\n" +
                   "      \"armor\": 15\n" +
                   "    },\n" +
                   "    \"spiroct\": {\n" +
                   "      \"health\": 460,\n" +
                   "      \"weapons.0.bullet.damage\": 37,\n" +
                   "      \"weapons.0.bullet.sapStrength\": 0,\n" +
                   "      \"weapons.2.bullet.damage\": 33,\n" +
                   "      \"weapons.2.bullet.sapStrength\": 0,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"cyerce\": {\n" +
                   "      \"health\": 860,\n" +
                   "      \"flying\": true,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"anthicus\": {\n" +
                   "      \"health\": 880,\n" +
                   "      \"weapons.0.bullet.spawnUnit.weapons.0.bullet.splashDamage\": 80,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"antumbra\": {\n" +
                   "      \"health\": 820,\n" +
                   "      \"weapons.0.bullet.damage\": 11,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 23,\n" +
                   "      \"weapons.5.bullet.damage\": 25,\n" +
                   "      \"armor\": 12\n" +
                   "    },\n" +
                   "    \"vanquish\": {\n" +
                   "      \"health\": 1560,\n" +
                   "      \"weapons.0.bullet.damage\": 85,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 35,\n" +
                   "      \"armor\": 22\n" +
                   "    },\n" +
                   "    \"arkyid\": {\n" +
                   "      \"health\": 2140,\n" +
                   "      \"weapons.0.bullet.sapStrength\": 0,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"vela\": {\n" +
                   "      \"health\": 1860,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"tecta\": {\n" +
                   "      \"health\": 1460,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"sei\": {\n" +
                   "      \"health\": 1540,\n" +
                   "      \"weapons.0.bullet.damage\": 21,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 23,\n" +
                   "      \"weapons.2.bullet.damage\": 35,\n" +
                   "      \"flying\": true,\n" +
                   "      \"armor\": 18\n" +
                   "    },\n" +
                   "    \"scepter\": {\n" +
                   "      \"health\": 2680,\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"toxopid\": {\n" +
                   "      \"health\": 9800,\n" +
                   "      \"weapons.0.shoot.shots\": 3,\n" +
                   "      \"weapons.1.shoot.shots\": 3,\n" +
                   "      \"weapons.0.bullet.damage\": 150,\n" +
                   "      \"weapons.2.bullet.splashDamage\": 25,\n" +
                   "      \"weapons.2.bullet.fragBullet.damage\": 15,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"RegenAbility\",\n" +
                   "          \"percentAmount\": 0.027\n" +
                   "        },\n" +
                   "        {\n" +
                   "          \"type\": \"SuppressionFieldAbility\",\n" +
                   "          \"range\": 240\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"aegires\": {\n" +
                   "      \"health\": 7800,\n" +
                   "      \"abilities.0\": {\n" +
                   "        \"damage\": 80,\n" +
                   "        \"maxTargets\": 80,\n" +
                   "        \"healPercent\": 4\n" +
                   "      },\n" +
                   "      \"flying\": true,\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"collaris\": {\n" +
                   "      \"health\": 8400,\n" +
                   "      \"targetAir\": true,\n" +
                   "      \"weapons.0.bullet.collidesAir\": true,\n" +
                   "      \"weapons.0.bullet.damage\": 150,\n" +
                   "      \"weapons.0.bullet.splashDamage\": 30,\n" +
                   "      \"weapons.0.bullet.fragBullet.damage\": 23,\n" +
                   "      \"weapons.0.bullet.fragBullet.splashDamage\": 16,\n" +
                   "      \"abilities.+=\": [{\n" +
                   "          \"type\": \"UnitSpawnAbility\",\n" +
                   "          \"spawnTime\": 900,\n" +
                   "          \"unit\": \"flare\",\n" +
                   "          \"spawnX\": 0,\n" +
                   "          \"spawnY\": -8\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"flare\": {\n" +
                   "      \"health\": 10,\n" +
                   "      \"fogRadius\": 48,\n" +
                   "      \"speed\": 5.2\n" +
                   "    },\n" +
                   "    \"eclipse\": {\n" +
                   "      \"health\": 10600,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"StatusFieldAbility\",\n" +
                   "          \"duration\": 240,\n" +
                   "          \"effect\": \"overclock\",\n" +
                   "          \"reload\": 120,\n" +
                   "          \"range\": 240\n" +
                   "        },\n" +
                   "        {\n" +
                   "          \"type\": \"ShieldRegenFieldAbility\",\n" +
                   "          \"amount\": 120,\n" +
                   "          \"max\": 360,\n" +
                   "          \"reload\": 240,\n" +
                   "          \"range\": 240\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 36\n" +
                   "    },\n" +
                   "    \"conquer\": {\n" +
                   "      \"health\": 12800,\n" +
                   "      \"flying\": true,\n" +
                   "      \"abilities.+=\": [\n" +
                   "        {\n" +
                   "          \"type\": \"ForceFieldAbility\",\n" +
                   "          \"radius\": 200,\n" +
                   "          \"regen\": 4.2,\n" +
                   "          \"max\": 9600,\n" +
                   "          \"cooldown\": 960\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"armor\": 42\n" +
                   "    }\n" +
                   "  }\n" +
                   "}"
    )

    loop(Dispatchers.game){
        state.teams.getActive().forEach {
            it.cores.forEach { c ->
                var amount = c.level() * max((Time.timeSinceMillis(startTime) / 1000 / 60 / 3).toInt(), 1)
                Groups.player.filter { p -> p.uuid() == c.lord() }.forEach { p ->
                    p.addCoin(amount / 2, true)
                    amount -= amount / 2
                }
                c.addCoin(amount)
                val text = buildString {
                    appendLine("[#${c.team.color}]${Iconc.blockCliff}${c.coins()}")
                    appendLine("$amount/s")
                    Groups.player.filter { p -> p.uuid() == c.lord() }.forEach {
                        appendLine("[white]${it.name}")
                    }
                    append("[white]")
                    append(c.levelText())
                }
                    Groups.player.filter { p ->
                               (p.within(c.x, c.y, 60f * 8f)
                            || (world.tileWorld(p.mouseX, p.mouseY) != null
                             && world.tileWorld(p.mouseX, p.mouseY).within(c.x, c.y, 30f * 8f)))
                             && fogControl.isVisible(p.team(), c.x, c.y)
                    }.forEach { p ->
                        Call.label(p.con, text, 1.013f, c.x, c.y)
                    }
                Units.nearby(null, c.x, c.y, 20 * 8f) { u ->
                    if (u.team == c.team && u.health < u.maxHealth) {
                        u.health += u.maxHealth / 100
                        u.clampHealth()
                        Call.transferItemEffect(Items.plastanium, c.x, c.y, u)
                    }
                }
            }
        }
        delay(1000)
    }
    loop(Dispatchers.game){
        Groups.player.forEach{
            val text = buildString {
                appendLine("[#${it.team().color}]金币:${it.coins()}")
                appendLine("军团等级:${it.unitType().level()}")
                appendLine("单位上限:${Groups.unit.filter { u -> u.owner() == it.uuid() }.size}/${it.unitCap()}")
                if (!it.checkCooldown())
                    append("[red]收取资源冷却时间:${cooldown[it.uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s")
                else
                    append("[green]收取资源冷却完毕")
                if (it.unitType().level() == 5)
                    if (!it.checkLordCooldown())
                        append("\n[red]领主降临冷却时间:${playerLordCooldown[it.uuid()] / 1000 - Time.timeSinceMillis(startTime) / 1000}s")
                    else
                        append("\n[green]领主降临冷却完毕")
                if (it.unitType().level() > 0) {
                    appendLine("[white]")
                    append("军团类型:${it.unitType()?.emoji()}")
                    if (it.unitType().level() >= 5 && it.lordUnitType() != null)
                        append("领主类型:${it.lordUnitType()?.emoji()}")
                }
            }
            Call.setHudText(it.con,text)
        }
        delay(100)
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    if (it.tile.block() is CoreBlock && it.tile.team() == player.team() &&
            it.tile.within(player.x,player.y, itemTransferRange)){
        launch(Dispatchers.game) { player.cityMenu(it.tile.build as CoreBuild) }
    }
}

listen<EventType.UnitControlEvent> {
    val unit: mindustry.gen.Unit = it.unit ?: return@listen
    val owner = unit.owner() ?: return@listen
    if (it.player.uuid() != owner){
        it.player.clearUnit()
        Call.announce(it.player.con(),"[red]你不是该单位的领主！无法控制")
    }else{
        launch(Dispatchers.game) {
            while(unit.isPlayer){
                unit.apply(StatusEffects.boss)
                unit.apply(StatusEffects.shielded, 1 * 60f)
                unit.apply(StatusEffects.overclock, 1 * 60f)
                unit.apply(StatusEffects.overdrive)
                yield()
            }
            unit.unapply(StatusEffects.boss)
            unit.unapply(StatusEffects.overdrive)
        }
    }
}

listen<EventType.PlayerChatEvent>{
    playerLastSendText[it.player.uuid()] = it.message
}
