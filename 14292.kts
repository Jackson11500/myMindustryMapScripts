@file:Depends("coreMindustry/utilNext", "调用菜单")
@file:Depends("coreMindustry/utilMapRule", "修改核心单位,单位属性")

package mapScript

import arc.graphics.Color
import arc.struct.ObjectIntMap
import arc.struct.ObjectMap
import arc.util.Align
import arc.util.Time
import coreLibrary.lib.util.loop
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import mindustry.Vars
import mindustry.content.*
import mindustry.entities.Units
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Iconc.*
import mindustry.gen.Player
import mindustry.type.StatusEffect
import mindustry.type.UnitType
import mindustry.world.Block
import mindustry.world.blocks.defense.turrets.ItemTurret
import mindustry.world.blocks.storage.CoreBlock
import mindustry.world.blocks.storage.CoreBlock.CoreBuild
import kotlin.math.ceil
import kotlin.random.Random

val menu = contextScript<coreMindustry.UtilNext>()

/**@author xkldklp*/

val playerMoney: ObjectIntMap<String> = ObjectIntMap()//个人贡献点
val teamMoney: ObjectMap<Team,Int> = ObjectMap()//团队资源点
val lastUnitType: ObjectMap<String,UnitType> = ObjectMap()//死前用的单位 还原所需要贡献点为单位容量*1.5
val missile = arrayOf(
    UnitTypes.anthicus.weapons.get(0).bullet.spawnUnit,
    UnitTypes.quell.weapons.get(0).bullet.spawnUnit,
    UnitTypes.disrupt.weapons.get(0).bullet.spawnUnit
)
val Tier1units = arrayOf(
    UnitTypes.flare
)
val Tier2units = arrayOf(
    UnitTypes.risso,
    UnitTypes.horizon
)
val Tier3units = arrayOf(
    UnitTypes.minke,
    UnitTypes.oxynoe,
    UnitTypes.locus
)
val Tier4units = arrayOf(
    UnitTypes.bryde,
    UnitTypes.cyerce,
    UnitTypes.precept,
    UnitTypes.mega,
    UnitTypes.anthicus
)
val Tier5units = arrayOf(
    UnitTypes.obviate,
    UnitTypes.sei,
    UnitTypes.antumbra,
    UnitTypes.vanquish,
    UnitTypes.tecta,
    UnitTypes.quell
)
val Tier6units = arrayOf(
    UnitTypes.omura,
    UnitTypes.eclipse,
    UnitTypes.conquer,
    UnitTypes.collaris
)
val T1ItemCap = 30
val T2ItemCap = 60
val T3ItemCap = 120
val T4ItemCap = 480
val T5ItemCap = 1440
val T6ItemCap = 2000

fun Player.addMoney(amount: Int){
    playerMoney.put(uuid(), playerMoney.get(uuid()) + amount)
    teamMoney.put(team(), teamMoney.get(team()) + amount)
    if(teamMoney.get(team()) <= 32000) return
    teamMoney.put(team(), 32000)
    playerMoney.put(uuid(), playerMoney.get(uuid()) + amount)
}
fun Player.removeMoney(amount: Int){
    playerMoney.put(uuid(),playerMoney.get(uuid()) - amount)
}
fun Player.getMoney(): Int {
    return playerMoney.get(uuid())
}
fun Player.checkMoney(amount: Int): Boolean {
    if(playerMoney.get(uuid()) >= amount) return true
    return false
}

fun mindustry.gen.Unit.maxShield(): Float{
    return (health * 2.5f).coerceAtMost(maxHealth * 2.5f)
}

fun itemDrop(amount: Int, x :Float, y :Float, maxRange: Float = 16f){
    if(amount == 0) return
    var dorpAmount = amount
    val dropX = x - maxRange / 2 + Random.nextFloat() * maxRange
    val dropY = y - maxRange / 2 + Random.nextFloat() * maxRange
    val dorpTime = Time.millis()
    launch(Dispatchers.game){
        while(true){
            delay(200)
            if(Time.millis() - dorpTime >= 30_000) break
            val units = buildList {
                Units.nearby(null, dropX, dropY, 54 * 8f) {
                    add(it)
                }
            }
            if (units.isEmpty()) continue
            units.forEach{
                if(it.within(dropX, dropY, it.hitSize + 4) && it.stack.amount < it.itemCapacity()){
                    if(it.stack.amount + dorpAmount > it.itemCapacity()) {
                        dorpAmount -= it.itemCapacity() - it.stack.amount
                        it.addItem(Items.copper, it.itemCapacity())
                    }else{
                        it.addItem(Items.copper,dorpAmount)
                        return@launch
                    }

                }
                Call.label(
                    it.player?.con ?: return@forEach, "$itemCopper * $dorpAmount\n[lightgray]${((Time.millis() - dorpTime) / 1000)}s/30s", 0.21f,
                    dropX, dropY
                )
            }
        }
    }
}

fun Player.upgrade(u: UnitType){
    val unit = unit()
    unit(u.spawn(team(), unit).apply {
        spawnedByCore = true
        if(!(u in Tier6units)) {
            if (type != UnitTypes.horizon && type != UnitTypes.locus && type != UnitTypes.precept && type != UnitTypes.vanquish && type != UnitTypes.conquer) {
                apply(StatusEffects.freezing, Float.MAX_VALUE)
            }
            apply(StatusEffects.electrified, Float.MAX_VALUE)
            apply(StatusEffects.sapped, Float.MAX_VALUE)
            if (type == UnitTypes.mega)
                apply(StatusEffects.muddy, Float.MAX_VALUE)
        }
        apply(StatusEffects.invincible, 4f * 60)
        shield = maxShield()
    })
    if(!(u in Tier6units)) lastUnitType.put(uuid(),u)
}

fun CoreBuild.upgrade(type: Block, text: String = ""){
    Vars.world.tile(Vars.world.width() - 3,Vars.world.height() - 3).setNet(Blocks.coreNucleus, team, 0)//临时核心保存核心资源
    tile.setNet(type, team, 0)
    Vars.world.tile(Vars.world.width() - 3,Vars.world.height() - 3).setNet(Blocks.air)
    broadcast("$text".with("text" to text), quite = true)
}

suspend fun Player.upgradeMenu() {
    if(lastUnitType.get(uuid()) != null && unit().type == UnitTypes.flare && lastUnitType.get(uuid()) != UnitTypes.flare){
        menu.sendMenuBuilder<Unit>(
            this, 30_000, "是否还原${lastUnitType.get(uuid()).emoji()}？",
            """
            [cyan]你之前的单位${lastUnitType.get(uuid()).emoji()}虽然已经阵亡 但是我们依然可以还原他
            [lightgray]还原死前用的单位所需要贡献点为那个单位容量*1.5(无法还原t6)
            你的贡献点：${getMoney()}
            预计消耗${(lastUnitType.get(uuid()).itemCapacity * 1.5).toInt()}
            [red]如果贡献点无法还原,那么你依然可以还原,但下次死亡你将无法再次还原此单位
            [lightgray]点了否也无法还原哦
        """.trimIndent()
        ) {
            add(listOf(
                "[green]是" to {
                    upgrade(lastUnitType.get(uuid()))
                    if(checkMoney((lastUnitType.get(uuid()).itemCapacity * 1.5).toInt()))
                        removeMoney((lastUnitType.get(uuid()).itemCapacity * 1.5).toInt())
                    else
                        lastUnitType.put(uuid(),UnitTypes.flare)
                },
                "[red]否" to {
                    lastUnitType.put(uuid(),UnitTypes.flare)
                    upgradeMenu()
                }
            ))
        }
    }else{
        menu.sendMenuBuilder<Unit>(
            this, 30_000, "升级菜单",
            """
            [cyan]单位物品容量满即可升级
            [lightgray]你需要${this.unit().stack.amount}/${this.unit().itemCapacity()}[white]$itemCopper[lightgray]来升级
            [cyan]队伍资源点：${teamMoney.get(team())}  个人贡献点：${getMoney()}
            [yellow]靠近核心即可上交资源 上交资源增加队伍资源点和个人贡献点
            [lightgray]还原死前用的单位所需要贡献点为那个单位容量*1.5
        """.trimIndent()
        ) {
            fun addUnitUpgrade(type: UnitType, needTeamMoney: Int = 0) {
                if(unit().stack().amount < unit().itemCapacity() || teamMoney.get(team()) < needTeamMoney) return
                val name = "升级为 ${type.emoji()}"
                add(listOf(name to suspend {
                    if (unit().stack().amount < unit().itemCapacity())//打开菜单后可能又会不满足条件
                        sendMessage("[red]单位背包资源不足")
                    else if (teamMoney.get(team()) < needTeamMoney)
                        sendMessage("[red]核心资源点不足")
                    else upgrade(type)
                }))
            }
            when(unit().type){
                //T1 -> T2
                UnitTypes.flare -> {
                    addUnitUpgrade(UnitTypes.risso)
                    addUnitUpgrade(UnitTypes.horizon)
                }
                //T2 -> T3
                UnitTypes.risso -> {
                    addUnitUpgrade(UnitTypes.minke)
                    addUnitUpgrade(UnitTypes.oxynoe)
                }
                UnitTypes.horizon -> {
                    addUnitUpgrade(UnitTypes.oxynoe)
                    addUnitUpgrade(UnitTypes.locus)
                }
                //T3 -> T4
                UnitTypes.minke -> {
                    addUnitUpgrade(UnitTypes.bryde,2400)
                    addUnitUpgrade(UnitTypes.cyerce,2400)
                }
                UnitTypes.oxynoe -> {
                    addUnitUpgrade(UnitTypes.cyerce,2400)
                    addUnitUpgrade(UnitTypes.precept,2400)
                    addUnitUpgrade(UnitTypes.mega,2400)
                }
                UnitTypes.locus -> {
                    addUnitUpgrade(UnitTypes.mega,2400)
                    addUnitUpgrade(UnitTypes.anthicus,2400)
                }
                //T4 -> T5
                UnitTypes.bryde -> {
                    addUnitUpgrade(UnitTypes.obviate,7200)
                    addUnitUpgrade(UnitTypes.sei,7200)
                }
                UnitTypes.cyerce -> {
                    addUnitUpgrade(UnitTypes.sei,7200)
                    addUnitUpgrade(UnitTypes.antumbra,7200)
                }
                UnitTypes.precept -> {
                    addUnitUpgrade(UnitTypes.antumbra,7200)
                    addUnitUpgrade(UnitTypes.vanquish,7200)
                }
                UnitTypes.mega -> {
                    addUnitUpgrade(UnitTypes.vanquish,7200)
                    addUnitUpgrade(UnitTypes.tecta,7200)
                }
                UnitTypes.anthicus -> {
                    addUnitUpgrade(UnitTypes.tecta,7200)
                    addUnitUpgrade(UnitTypes.quell,7200)
                }
                //T5 -> T6
                UnitTypes.obviate -> {
                    addUnitUpgrade(UnitTypes.omura,16800)
                    addUnitUpgrade(UnitTypes.eclipse,16800)
                }
                UnitTypes.sei -> {
                    addUnitUpgrade(UnitTypes.omura,16800)
                    addUnitUpgrade(UnitTypes.eclipse,16800)
                }
                UnitTypes.antumbra -> {
                    addUnitUpgrade(UnitTypes.eclipse,16800)
                    addUnitUpgrade(UnitTypes.conquer,16800)
                }
                UnitTypes.vanquish -> {
                    addUnitUpgrade(UnitTypes.eclipse,16800)
                    addUnitUpgrade(UnitTypes.conquer,16800)
                }
                UnitTypes.tecta -> {
                    addUnitUpgrade(UnitTypes.conquer,16800)
                    addUnitUpgrade(UnitTypes.collaris,16800)
                }
                UnitTypes.quell -> {
                    addUnitUpgrade(UnitTypes.conquer,16800)
                    addUnitUpgrade(UnitTypes.collaris,16800)
                }
            }
            if(unit().stack.amount != 0 && unit().within(core().x,core().y,Vars.itemTransferRange)) add(listOf(
                "上交全部资源${unit().stack.amount}" to {
                    addMoney(unit().stack.amount)
                    unit().stack.amount = 0
                },
                "上交1/4资源${(unit().stack.amount / 4).toInt()}" to {
                    addMoney((unit().stack.amount / 4).toInt())
                    unit().stack.amount -= (unit().stack.amount / 4).toInt()
                    upgradeMenu()
                }
            )) else if(unit().stack.amount != 0) add(listOf(
                "远程上交全部资源(50%手续费)\n向下取整\n${unit().stack.amount}->${(unit().stack.amount / 2).toInt()}" to {
                    addMoney((unit().stack.amount / 2).toInt())
                    unit().stack.amount = 0
                },
                "上交1/4资源(50%手续费)\n向下取整\n${(unit().stack.amount / 4).toInt()}->${(unit().stack.amount / 8).toInt()}" to {
                    addMoney((unit().stack.amount / 8).toInt())
                    unit().stack.amount -= (unit().stack.amount / 4).toInt()
                    upgradeMenu()
                }
            ))


            add(listOf(
                "取消" to {},
                "buff商店" to {buffMenu()},
                "单位详细属性" to {infoMenu()}
            ))
            if(admin){
                //测试用
                add(listOf(
                    "[red]<ADMIN>装填背包" to {unit().stack.amount = unit().itemCapacity()},
                    "[red]<ADMIN>上交1k资源" to {addMoney(1000)}
                ))
                add(listOf(
                    "[red]<ADMIN>白做1k贡献" to {removeMoney(1000)},
                    "[red]<ADMIN>丢失1k资源点" to {teamMoney.put(team(),teamMoney.get(team()) - 1000)}
                ))
            }
        }
    }
}

suspend fun Player.buffMenu(){
    if(unit().type in Tier6units){
        sendMessage("[red]Tier6单位无法购买buff")
        return
    }
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "buff菜单",
        """
            [yellow]使用贡献点购买/清除 buff/debuff
            [lightgray]仅debuff可以使用未上交的矿物支付(优先使用未上交的矿物)
            [cyan]队伍资源点：${teamMoney.get(team())}  个人贡献点：${getMoney()}
            [red]Tier6单位无法购买属性
        """.trimIndent()
    ) {
        var allBuffCost = 0

        fun addEffect(effect: StatusEffect, cost: Int): Pair<String, suspend () -> Unit>? {
            return if (!unit().hasEffect(effect)) {
                allBuffCost += cost
                "添加 ${effect.emoji()}\n${cost}贡献点" to suspend {
                    if (!checkMoney(cost))
                        sendMessage("[red]贡献点不足")
                    else {
                        removeMoney(cost)
                        unit().apply(effect, Float.MAX_VALUE)
                    }
                }
            } else null
        }

        fun removeEffect(effect: StatusEffect, cost: Int): Pair<String, suspend () -> Unit>? {
            return if (unit().hasEffect(effect)) {
                allBuffCost += cost
                "移除 ${effect.emoji()}\n${cost}矿物(优先)/贡献点" to suspend {
                    if (!checkMoney(cost) && unit().stack.amount < cost)
                        sendMessage("[red]贡献点或矿物不足")
                    else {
                        if(unit().stack.amount >= cost)
                            unit().stack.amount -= cost
                        else
                            removeMoney(cost)
                        unit().unapply(effect)
                    }
                }
            } else null
        }

        //buff增减
        val debuffList = listOfNotNull(
            removeEffect(StatusEffects.electrified, (unit().itemCapacity() * 0.25f).toInt()),
            removeEffect(StatusEffects.sapped, (unit().itemCapacity() * 0.25f).toInt()),
            removeEffect(StatusEffects.freezing, (unit().itemCapacity() * 0.25f).toInt())
        )
        if (debuffList.any()) this += debuffList
        val buffList = listOfNotNull(
            addEffect(StatusEffects.overclock, (unit().itemCapacity() * 0.4f).toInt()),
            addEffect(StatusEffects.overdrive, (unit().itemCapacity() * 0.8f).toInt()),
            addEffect(StatusEffects.boss, (unit().itemCapacity() * 0.8f).toInt())
        )
        if (buffList.any()) this += buffList

        if (allBuffCost > 0) {
            add(listOf("一键购买所有buff选项\n${allBuffCost}贡献点" to suspend {
                if (!checkMoney(allBuffCost))
                    sendMessage("[red]贡献点不足")
                else {
                    removeMoney(allBuffCost)
                    unit().apply {
                        unapply(StatusEffects.electrified)
                        unapply(StatusEffects.sapped)
                        unapply(StatusEffects.freezing)
                        apply(StatusEffects.overclock, Float.MAX_VALUE)
                        apply(StatusEffects.overdrive, Float.MAX_VALUE)
                        apply(StatusEffects.boss, Float.MAX_VALUE)
                    }
                }
                Unit
            }
            ))
        }
        add(listOf(
            "取消" to {},
            "升级商店" to {upgradeMenu()}
        ))
    }
}

suspend fun Player.infoMenu() {
    menu.sendMenuBuilder<Unit>(
        this, 30_000, "单位详细",
        """
            查看各个单位的属性和可升级兵种
        """.trimIndent()
    ) {
        fun showInfo(type: UnitType, upgradeType1: UnitType? = null, upgradeType2: UnitType? = null, upgradeType3: UnitType? = null): Pair<String, suspend () -> Unit> {
            return "${type.emoji()}" to suspend {
                val text = buildString{
                    appendLine("${type.emoji()}${type.localizedName}${type.emoji()}")
                    appendLine("生命值${type.health}/护甲值${type.armor}")
                    type.weapons.each {
                        appendLine(" ${it.name}")
                        appendLine("  ${it.reload / 60f}s 射击一次")
                        if(it.shootStatus != StatusEffects.none)
                            appendLine("  ${it.shootStatus.emoji()}${it.shootStatus}射击效果/${it.shootStatusDuration / 60f}s")
                        appendLine("   ${it.bullet.damage}伤害")
                        if(it.bullet.splashDamage > 0) {
                            appendLine("   ${it.bullet.splashDamage}范围伤害")
                            appendLine("   ${it.bullet.splashDamageRadius}范围伤害范围")
                        }
                        if(it.bullet.spawnUnit != null){
                            appendLine("  ${it.bullet.spawnUnit.emoji()}${it.bullet.spawnUnit.weapons.get(0).bullet.splashDamage}导弹伤害")
                            if(it.bullet.spawnUnit.weapons.get(0).bullet.status != StatusEffects.none)
                                appendLine("   ${it.bullet.spawnUnit.weapons.get(0).bullet.status.emoji()}${it.bullet.spawnUnit.weapons.get(0).bullet.status}导弹效果/${it.bullet.spawnUnit.weapons.get(0).bullet.statusDuration / 60f}s")
                        }
                        if(it.bullet.status != StatusEffects.none) {
                            appendLine("   ${it.bullet.status.emoji()}${it.bullet.status}子弹效果/${it.bullet.statusDuration / 60f}s")
                        }
                        if(it.bullet.fragBullet != null){
                            appendLine("   ${it.bullet.fragBullets}个分裂子弹")
                            appendLine("    ${it.bullet.fragBullet.damage}伤害")
                            if(it.bullet.fragBullet.splashDamage > 0) {
                                appendLine("    ${it.bullet.fragBullet.splashDamage}范围伤害")
                                appendLine("    ${it.bullet.fragBullet.splashDamageRadius}范围伤害范围")
                            }
                        }
                    }
                    if(upgradeType1 != null)
                        appendLine("${type.emoji()} -> ${upgradeType1.emoji()}")
                    if(upgradeType2 != null)
                        appendLine("${type.emoji()} -> ${upgradeType2.emoji()}")
                    if(upgradeType3 != null)
                        appendLine("${type.emoji()} -> ${upgradeType3.emoji()}")
                }
                sendMessage(text)
            }
        }
        add(listOf(
            showInfo(UnitTypes.flare,UnitTypes.risso,UnitTypes.horizon)
        ))
        add(listOf(
            showInfo(UnitTypes.risso,UnitTypes.minke,UnitTypes.oxynoe),
            showInfo(UnitTypes.horizon,UnitTypes.oxynoe,UnitTypes.locus)
        ))
        add(listOf(
            showInfo(UnitTypes.minke, UnitTypes.bryde, UnitTypes.cyerce),
            showInfo(UnitTypes.oxynoe, UnitTypes.cyerce, UnitTypes.precept, UnitTypes.mega),
            showInfo(UnitTypes.locus, UnitTypes.mega, UnitTypes.anthicus)
        ))
        add(listOf(
            showInfo(UnitTypes.bryde, UnitTypes.obviate, UnitTypes.sei),
            showInfo(UnitTypes.cyerce, UnitTypes.sei, UnitTypes.antumbra),
            showInfo(UnitTypes.precept, UnitTypes.sei, UnitTypes.vanquish),
            showInfo(UnitTypes.mega, UnitTypes.vanquish, UnitTypes.tecta),
            showInfo(UnitTypes.anthicus, UnitTypes.tecta, UnitTypes.quell)
        ))
        add(listOf(
            showInfo(UnitTypes.obviate, UnitTypes.omura, UnitTypes.eclipse),
            showInfo(UnitTypes.sei, UnitTypes.omura, UnitTypes.eclipse),
            showInfo(UnitTypes.antumbra, UnitTypes.eclipse, UnitTypes.conquer),
            showInfo(UnitTypes.vanquish, UnitTypes.eclipse, UnitTypes.conquer),
            showInfo(UnitTypes.tecta, UnitTypes.conquer, UnitTypes.collaris),
            showInfo(UnitTypes.quell, UnitTypes.conquer, UnitTypes.collaris)
        ))
        add(listOf(
            showInfo(UnitTypes.omura),
            showInfo(UnitTypes.eclipse),
            showInfo(UnitTypes.conquer),
            showInfo(UnitTypes.collaris)
        ))
    }
}

val copperToCoreHealth by lazy { state.rules.tags.getInt("@copperToCoreHealth", 7) }
val copperToUnitHealth by lazy { state.rules.tags.getInt("@copperToUnitHealth", 3) }
onEnable {
    contextScript<coreMindustry.UtilMapRule>().apply {
        //核心属性
        registerMapRule((Blocks.coreShard as CoreBlock)::unitType) { UnitTypes.flare }
        registerMapRule((Blocks.coreShard as CoreBlock)::health) { 8000 }
        registerMapRule((Blocks.coreFoundation as CoreBlock)::unitType) { UnitTypes.flare }
        registerMapRule((Blocks.coreFoundation as CoreBlock)::health) { 16000 }
        registerMapRule((Blocks.coreNucleus as CoreBlock)::unitType) { UnitTypes.flare }
        registerMapRule((Blocks.coreNucleus as CoreBlock)::health) { 24000 }

        //T1
        //flare
        var unitType = UnitTypes.flare
        registerMapRule(unitType::itemCapacity) { T1ItemCap }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 12f }
        registerMapRule(unitType.weapons.get(0).bullet::buildingDamageMultiplier) { 1.5f }
        registerMapRule(unitType::armor) { 0f }

        //T2
        //risso,horzion
        unitType = UnitTypes.risso
        registerMapRule(unitType::itemCapacity) { T2ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 140f }
        registerMapRule(unitType::armor) { 0f }

        unitType = UnitTypes.horizon
        registerMapRule(unitType::itemCapacity) { T2ItemCap }
        registerMapRule(unitType.weapons.get(0).bullet::collidesAir) { true }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 4f * 60 }
        registerMapRule(unitType::health) { 260f }
        registerMapRule(unitType::armor) { 0f }


        //T3
        //minke,oxynoe,locus
        unitType = UnitTypes.minke
        registerMapRule(unitType::itemCapacity) { T3ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 320f }
        registerMapRule(unitType.weapons.get(2).bullet::damage) { 120f }
        registerMapRule(unitType.weapons.get(2).bullet::collidesAir) { true }
        registerMapRule(unitType.weapons.get(2).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(2).bullet::statusDuration) { 4f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamage) { 15f }
        registerMapRule(unitType::armor) { 0f }

        unitType = UnitTypes.oxynoe
        registerMapRule(unitType::itemCapacity) { T3ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 320f }
        registerMapRule(unitType.weapons.get(0).bullet::collidesAir) { true }
        registerMapRule(unitType::armor) { 0f }

        unitType = UnitTypes.locus
        registerMapRule(unitType::itemCapacity) { T3ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 400f }
        registerMapRule(unitType::armor) { 5f }
        registerMapRule(unitType.weapons.get(0)::rotateSpeed) { Float.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 24f }


        //T4
        //bryde,cyerce,precept,mega,anthicus
        unitType = UnitTypes.bryde
        registerMapRule(unitType::itemCapacity) { T4ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 650f }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 140f }
        registerMapRule(unitType.weapons.get(0).bullet::collidesAir) { true }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 8f * 60 }
        registerMapRule(unitType.weapons.get(2).bullet::buildingDamageMultiplier) { 0.5f }

        unitType = UnitTypes.cyerce
        registerMapRule(unitType::itemCapacity) { T4ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 550f }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType.weapons.get(2)::shootStatus) { StatusEffects.slow }
        registerMapRule(unitType.weapons.get(2)::shootStatusDuration) { 0.8f * 60 }

        unitType = UnitTypes.precept
        registerMapRule(unitType::itemCapacity) { T4ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::health) { 750f }
        registerMapRule(unitType::armor) { 7f }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 85f }
        registerMapRule(unitType.weapons.get(0)::rotateSpeed) { Float.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet.fragBullet::damage) { 15f }
        registerMapRule(unitType.weapons.get(0).bullet.fragBullet::buildingDamageMultiplier) { 2f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.sporeSlowed }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 1.6f * 60 }

        unitType = UnitTypes.mega
        registerMapRule(unitType::itemCapacity) { T4ItemCap }
        registerMapRule(unitType::health) { 600f }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType.weapons.get(0).bullet::buildingDamageMultiplier) { 1.5f }
        registerMapRule(unitType.weapons.get(2).bullet::buildingDamageMultiplier) { 1.5f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 3f * 60 }
        registerMapRule(unitType.weapons.get(2).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(2).bullet::statusDuration) { 1f * 60 }

        unitType = UnitTypes.anthicus
        registerMapRule(unitType::itemCapacity) { T4ItemCap }
        registerMapRule(unitType::legCount) { 0 }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::itemCapacity) { Int.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::splashDamage) { 110f }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::lifetime) { 2.4f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::health) { 60f }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::rotateSpeed) { 5f }
        registerMapRule(unitType::health) { 600f }
        registerMapRule(unitType::armor) { 0f }


        //T5
        //obviate,sei,antumbra,vanquish,tecta,quell
        unitType = UnitTypes.obviate
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 1000f }

        unitType = UnitTypes.sei
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 800f }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 15f }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamage) { 7f }
        registerMapRule(unitType.weapons.get(1).bullet::damage) { 24f }

        unitType = UnitTypes.antumbra
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 1200f }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 9f }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamage) { 18f }
        registerMapRule(unitType.weapons.get(5).bullet::damage) { 33f }

        unitType = UnitTypes.vanquish
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 10f }
        registerMapRule(unitType::health) { 1200f }
        registerMapRule(unitType.weapons.get(0)::rotateSpeed) { Float.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet.fragBullet::splashDamage) { 7f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.sporeSlowed }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 1.6f * 60 }

        unitType = UnitTypes.tecta
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType::legCount) { 0 }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 800f }
        registerMapRule(unitType.weapons.get(0)::shootStatus) { StatusEffects.slow }
        registerMapRule(unitType.weapons.get(0)::shootStatusDuration) { 2f * 60 }
        registerMapRule(unitType.weapons.get(1)::shootStatus) { StatusEffects.slow }
        registerMapRule(unitType.weapons.get(1)::shootStatusDuration) { 2f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamage) { 30f }

        unitType = UnitTypes.quell
        registerMapRule(unitType::itemCapacity) { T5ItemCap }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::itemCapacity) { Int.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::splashDamage) { 320f }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::lifetime) { 2.4f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::health) { 90f }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit::rotateSpeed) { 5f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.slow }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 3f * 60 }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 1000f }


        //T6
        //omura,navanax,eclipse,conquer,collaris,disrupt
        unitType = UnitTypes.omura
        registerMapRule(unitType::itemCapacity) { T6ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 2400f }
        registerMapRule(unitType.weapons.get(0)::reload) { 4f * 60 }
        registerMapRule(unitType.weapons.get(0)::shootStatus) { StatusEffects.electrified }
        registerMapRule(unitType.weapons.get(0)::shootStatusDuration) { 4f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet::damage) { 300f }
        registerMapRule(unitType.weapons.get(0).bullet::spawnUnit) { (Blocks.scathe as ItemTurret).ammoTypes[Items.carbide].spawnUnit }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::splashDamage) { 1000f }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(0).bullet.spawnUnit.weapons.get(0).bullet::statusDuration) { 20f * 60 }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamage) { 60f }
        registerMapRule(unitType.weapons.get(0).bullet::splashDamageRadius) { 80f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.slow }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 3f * 60 }


        unitType = UnitTypes.eclipse
        registerMapRule(unitType::itemCapacity) { T6ItemCap }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 2800f }
        registerMapRule(unitType.weapons.get(2).bullet::splashDamage) { 25f }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.corroded }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 10f * 60 }

        unitType = UnitTypes.conquer
        registerMapRule(unitType::itemCapacity) { T6ItemCap }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 15f }
        registerMapRule(unitType::health) { 2800f }
        registerMapRule(unitType.weapons.get(0)::rotateSpeed) { Float.MAX_VALUE }
        registerMapRule(unitType.weapons.get(0).bullet::status) { StatusEffects.sporeSlowed }
        registerMapRule(unitType.weapons.get(0).bullet::statusDuration) { 3.2f * 60 }

        unitType = UnitTypes.collaris
        registerMapRule(unitType::itemCapacity) { T6ItemCap }
        registerMapRule(unitType::legCount) { 0 }
        registerMapRule(unitType::flying) { true }
        registerMapRule(unitType::armor) { 0f }
        registerMapRule(unitType::health) { 1800f }
        registerMapRule(unitType.weapons.get(0)::shootStatus) { StatusEffects.unmoving }
        registerMapRule(unitType.weapons.get(0)::shootStatusDuration) { 5f * 60 }

    }
    Vars.state.rules.apply{
        unitCap = 999999
        fire = false
        bannedUnits.add(UnitTypes.flare)
    }
    Vars.state.teams.getActive().each{
        teamMoney.put(it.team,250)
        launch(Dispatchers.game){
            delay(5000L)
            it.core().health += 20000f
        }
    }
    loop(Dispatchers.game){
        Groups.player.each{ p ->
            var text = ""
            text += buildString {
                val unit = p.unit() ?: return@buildString
                if(unit.maxHealth <= 1) return@buildString//玩家即使不操控任何单位 也会有一个血量上限0.5的单位给玩家操控
                appendLine("---${unit.type.emoji()}---[yellow]每个铜能恢复单位${copperToUnitHealth}点血量")
                append("[green]$add")
                repeat((unit.health / unit.maxHealth * 10).toInt()) {
                    append("[green]|")
                }
                repeat(10 - (unit.health / unit.maxHealth * 10).toInt()) {
                    append("[red]|")
                }
                append("[white]${unit.health}/${unit.maxHealth}|")
                append("[yellow]${modeSurvival}")
                repeat((unit.shield / unit.maxShield() * 10).toInt()) {
                    append("[green]|")
                }
                repeat(10 - (unit.shield / unit.maxShield() * 10).toInt()) {
                    append("[red]|")
                }
                appendLine("[white]${unit.shield}/${unit.maxShield()}")
                appendLine("$itemCopper[white]${unit.stack.amount}/${unit.itemCapacity()}[yellow]$defense[white] ${unit.stack.amount * copperToUnitHealth}/${unit.itemCapacity() * copperToUnitHealth}")
            }
            text += buildString {
                val core = p.core() ?: return@buildString
                appendLine("---${core.block.emoji()}---[yellow]每个资源点能恢复核心${copperToCoreHealth}点血量")
                append("[cyan]核心资源点贮藏:${teamMoney.get(p.team())}")
                if(p.checkMoney(1)) appendLine("   [cyan]个人贡献点:${p.getMoney()}[purple]") else appendLine("[purple]")
                when (core.block) {
                    Blocks.coreShard -> appendLine("核心等级:1(单位Tier 3) 下一级所需资源点：2400")
                    Blocks.coreFoundation -> appendLine("核心等级:2(单位Tier 4) 下一级所需资源点：7200")
                    Blocks.coreNucleus ->
                        if (teamMoney.get(p.team()) <= 16800) appendLine("核心等级:3(单位Tier 5) 下一级所需资源点：16800") else appendLine("核心等级:4(单位Tier 6) 已经满级")
                }
            }
            text += buildString {
                state.teams.getActive().joinToString("\n") {
                    val core = it.core() ?: return@joinToString ""
                    append("[#${it.team.color}]${it.team.name}[white]${core.block.emoji()}   ${teamMoney.get(it.team)}")
                    if(teamMoney.get(it.team) <= 16800) appendLine("") else appendLine("   [gold]TIER6允许升级")
                }
                append("[red]STARBLAST破烂平衡测试中")
            }
            Call.infoPopup(
                p.con, text, 0.51f,
                Align.topLeft, 350, 0, 0, 0
            )
        }
        delay(500)
    }
    loop(Dispatchers.game){
        Groups.unit.each{
            //单位边界穿梭
            if (it.x / 8 >= Vars.world.width() - 1) it.set(24f, it.y)
            if (it.x / 8 <= 1) it.set(Vars.world.width() * 8f - 24, it.y)
            if (it.y / 8 >= Vars.world.height() - 1) it.set(it.x, 24f)
            if (it.y / 8 <= 1) it.set(it.x, Vars.world.height() * 8f - 24)
            //单位回血
            val use = ((it.maxHealth - it.health) / copperToUnitHealth).toInt()
                .coerceAtMost(it.stack().amount)
            if (use > 0) {
                it.stack.amount -= use
                it.health += use * copperToUnitHealth
                itemDrop(ceil(use * 0.7).toInt(), it.x, it.y, it.hitSize * 4f)
            }
            //核心附近的flare无敌
            val core = it.core() ?: return@each
            if(it.type == UnitTypes.flare && it.within(core.x, core.y, core.hitSize()))
                it.apply(StatusEffects.invincible, 1f * 60)
        }
        Groups.bullet.each{
            //子弹边界穿梭
            val vel = it.vel
            if(it.x / 8 >= Vars.world.width() - 1) it.set(8f,it.y)
            if(it.x / 8 <= 0) it.set(Vars.world.width() * 8f - 8,it.y)
            if(it.y / 8 >= Vars.world.height() - 1) it.set(it.x,8f)
            if(it.y / 8 <= 0) it.set(it.x,Vars.world.height() * 8f - 8)
            it.vel = vel
        }
        state.teams.getActive().each { data ->
            val core = data.core() ?: return@each
            //核心升降级
            when (core.block) {
                Blocks.coreShard ->
                    if(teamMoney.get(data.team) >= 2400)
                        core.upgrade(Blocks.coreFoundation, "[#${data.team.color}]${data.team.name}[white]已经完成了次代核心升级")
                Blocks.coreFoundation ->
                    if(teamMoney.get(data.team) >= 7200)
                        core.upgrade(Blocks.coreNucleus, "[#${data.team.color}]${data.team.name}[white]已经完成了终代核心升级\n" +
                    "[red]将队伍资源点填充到16800即可开放tier6单位升级！")
                    else if(teamMoney.get(data.team) < 2400)
                        core.upgrade(Blocks.coreShard, "[#${data.team.color}]${data.team.name}[white]的资源不足以支撑次代核心运作,降级为初代核心")
                Blocks.coreNucleus ->
                    if(teamMoney.get(data.team) < 7200)
                        core.upgrade(Blocks.coreFoundation, "[#${data.team.color}]${data.team.name}[white]的资源不足以支撑终代核心运作,降级为次代核心")
            }
            //核心血量不足1/2,紧急回血
            if (teamMoney.get(data.team) > core.maxHealth / copperToCoreHealth * 4 && core.health <= core.maxHealth / 2) {
                Call.effect(Fx.impactReactorExplosion,core.x,core.y, 0f, Color.red)
                repeat(16) {
                    itemDrop(ceil(teamMoney.get(data.team) * 0.75 / 8).toInt(), core.x, core.y, core.hitSize() * 16)
                }
                teamMoney.put(data.team, (teamMoney.get(data.team) * 0.25).toInt())
                core.health = core.maxHealth
            }
        }
        yield()
    }
    loop(Dispatchers.game){
        state.teams.getActive().each { data ->
            val core = data.core() ?: return@each
            if(core.health >= core.maxHealth - copperToCoreHealth && core.health < core.maxHealth * 1.5)
                //核心护盾
                core.health += core.maxHealth * 0.05f
            else{
                //核心常规回血
                val use = ((core.maxHealth - core.health) / copperToCoreHealth).toInt()
                    .coerceAtMost(teamMoney.get(data.team))
                if (use > 0) {
                    teamMoney.put(data.team, teamMoney.get(data.team) - use)
                    core.health += use * copperToCoreHealth
                    itemDrop(ceil(use * 0.7).toInt(), core.x, core.y, core.hitSize() * 2)
                    Call.effect(Fx.explosion,core.x,core.y, 0f, Color.red)
                }
            }
        }
        Groups.unit.each {
            //中毒阻回盾
            if(it.hasEffect(StatusEffects.corroded)) return@each
            //单位护盾
            if(it.shield <= it.maxShield())
                it.shield += it.maxShield() * 0.08f
            if(it.shield > it.maxShield())
                it.shield = it.maxShield()
        }
        delay(4000)
    }
    val oreGrowSpeed = state.rules.tags.getInt("@oreGrowSpeed", 16)
    loop(Dispatchers.game){
        delay(1000)
        repeat(oreGrowSpeed){
            val tile = Vars.world.tiles.getn(
                Random.nextInt(0, world.width()),
                Random.nextInt(0, world.height())
            )
            if(tile.floor() == Blocks.stone)
                tile.setNet(Blocks.copperWall, Team.crux, 0)
        }
    }
}

listen<EventType.TapEvent> {
    val player = it.player
    if (player.dead()) return@listen
    if ((it.tile.block() is CoreBlock && it.tile.team() == player.team()) ||
        (player.unit().within(it.tile.worldx(), it.tile.worldy(), player.unit().hitSize * 1.5f) && !player.unit().within(it.tile.worldx(), it.tile.worldy(), player.unit().hitSize))) {
        //点击核心或单位外圈打开菜单
        launch(Dispatchers.game) { player.upgradeMenu() }
    }
}

val oreCostIncreaseTime = state.rules.tags.getInt("@oreCostIncreaseTime", 480) * 1000f
val startTime by lazy { Time.millis() }
listen<EventType.BlockDestroyEvent> { t ->
    val time = (Time.timeSinceMillis(startTime) / oreCostIncreaseTime).toInt()
    val amount = Random.nextInt(time + 1, time + 3)
    itemDrop(amount, t.tile.worldx(), t.tile.worldy())
}

listen<EventType.UnitDestroyEvent> { u ->
    if(u.unit.type in missile)
        itemDrop(u.unit.stack.amount, u.unit.x, u.unit.y)
    else
        itemDrop(u.unit.stack.amount + (u.unit.itemCapacity() * 0.4f).toInt(), u.unit.x, u.unit.y)
}
