const apiBase = "http://localhost:25567/api";
const particles = ["FIREWORK", "FLAME", "HEART", "NOTE", "SMOKE", "VILLAGER_HAPPY"];
const chatColors = ["normal", "§c", "§b", "§a", "§e", "§d", "rainbow", "random", "gradient"];
let availableGroups = [];
let allPlayers = [];

function fetchPlayers() {
    fetch(`${apiBase}/list_players`)
        .then(res => res.json())
        .then(data => {
            const status = document.getElementById("status");
            status.textContent = data.status === "success" ? "在线" : "离线";
            status.classList.toggle("online", data.status === "success");
            status.classList.toggle("offline", data.status !== "success");
            allPlayers = data.players || [];
            renderPlayers(allPlayers);
        })
        .catch(err => {
            const status = document.getElementById("status");
            status.textContent = "离线";
            status.classList.add("offline");
            console.error("请求失败:", err);
        });
}

function renderPlayers(players) {
    const playerList = document.getElementById("player-list");
    playerList.innerHTML = "";
    players.forEach(player => {
        const card = document.createElement("div");
        card.className = "player-card";
        card.id = `player-${player.uuid}`;
        const currentGroup = player.groups && player.groups.length > 0 ? player.groups[0] : "member";
        card.innerHTML = `
            <div class="accordion">
                <input type="checkbox" id="toggle-${player.uuid}" class="accordion-toggle" checked>
                <label for="toggle-${player.uuid}" class="accordion-header"><i class="fas fa-user"></i> ${player.name} (${player.online ? "在线" : "离线"})</label>
                <div class="accordion-content">
                    <p>UUID: ${player.uuid}</p>
                    <p>
                        <label>积分:</label>
                        <input type="number" value="${player.score}" id="score-${player.uuid}" class="input-field">
                        <button onclick="setScore('${player.uuid}', document.getElementById('score-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="score-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>Rank:</label>
                        <select id="rank-${player.uuid}" onchange="toggleCustomRank('${player.uuid}')" class="input-field">
                            <option value="VIP" ${player.rank === "VIP" ? "selected" : ""}>VIP</option>
                            <option value="SVIP" ${player.rank === "SVIP" ? "selected" : ""}>SVIP</option>
                            <option value="VVIP" ${player.rank === "VVIP" ? "selected" : ""}>VVIP</option>
                            <option value="UVIP" ${player.rank === "UVIP" ? "selected" : ""}>UVIP</option>
                            <option value="EVIP" ${player.rank === "EVIP" ? "selected" : ""}>EVIP</option>
                            <option value="custom" ${!["VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? "selected" : ""}>自定义</option>
                        </select>
                        <input id="custom-rank-${player.uuid}" placeholder="输入自定义 Rank" value="${!["VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? player.rank : ""}" class="input-field" style="display: ${!["VIP", "SVIP", "VVIP", "UVIP", "EVIP"].includes(player.rank) ? 'inline-block' : 'none'};">
                        <button onclick="setRank('${player.uuid}', document.getElementById('rank-${player.uuid}').value === 'custom' ? document.getElementById('custom-rank-${player.uuid}').value : document.getElementById('rank-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="rank-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>身份组:</label>
                        <select id="group-${player.uuid}" class="input-field">
                            ${availableGroups.map(g => `<option value="${g}" ${currentGroup === g ? "selected" : ""}>${g}</option>`).join("")}
                        </select>
                        <button onclick="setGroup('${player.uuid}', document.getElementById('group-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="group-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>进服粒子:</label>
                        <select id="particle-${player.uuid}" class="input-field">
                            ${particles.map(p => `<option value="${p}" ${p === player.join_particle ? "selected" : ""}>${p}</option>`).join("")}
                        </select>
                        <button onclick="setParticle('${player.uuid}', document.getElementById('particle-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="particle-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>进服消息:</label>
                        <input id="join-message-${player.uuid}" value="${player.join_message}" class="input-field">
                        <button onclick="setJoinMessage('${player.uuid}', document.getElementById('join-message-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="join-message-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>发言颜色:</label>
                        <select id="chat-color-${player.uuid}" class="input-field">
                            ${chatColors.map(c => `<option value="${c}" ${c === player.chat_color ? "selected" : ""}>${c === "normal" ? "默认" : c === "rainbow" ? "彩虹" : c === "random" ? "随机" : c === "gradient" ? "渐变" : c}</option>`).join("")}
                        </select>
                        <button onclick="setChatColor('${player.uuid}', document.getElementById('chat-color-${player.uuid}').value)" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="chat-color-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>
                        <label>显示选项:</label>
                        <input type="checkbox" id="show-rank-${player.uuid}" ${player.show_rank ? "checked" : ""}> Rank
                        <input type="checkbox" id="show-vip-${player.uuid}" ${player.show_vip ? "checked" : ""}> VIP
                        <input type="checkbox" id="show-group-${player.uuid}" ${player.show_group ? "checked" : ""}> 身份组
                        <button onclick="setDisplayOptions('${player.uuid}')" class="btn primary-btn"><i class="fas fa-check"></i></button>
                        <span id="display-options-status-${player.uuid}" class="status"></span>
                    </p>
                    <p>封禁状态: ${player.ban_until === 0 ? "未封禁" : "已封禁"}</p>
                    <p>封禁原因: ${player.ban_reason || "无"}</p>
                    <button onclick="banPlayer('${player.uuid}')" class="btn primary-btn"><i class="fas fa-ban"></i> 封禁</button>
                    <button onclick="unbanPlayer('${player.uuid}')" class="btn primary-btn"><i class="fas fa-unlock"></i> 解封</button>
                    <span id="ban-status-${player.uuid}" class="status"></span>
                </div>
            </div>
        `;
        playerList.appendChild(card);
    });
}

function searchPlayers() {
    const keyword = document.getElementById("search-input").value.toLowerCase();
    const filteredPlayers = allPlayers.filter(player => player.name.toLowerCase().includes(keyword));
    renderPlayers(filteredPlayers);
}

function fetchGroups() {
    fetch(`${apiBase}/list_groups`)
        .then(res => res.json())
        .then(data => {
            if (data.status === "success") {
                availableGroups = data.groups.map(g => g.name);
                const groupList = document.getElementById("group-list");
                groupList.innerHTML = "";
                data.groups.forEach(group => {
                    const div = document.createElement("div");
                    const colorStyle = group.name === "op" ? "op-group" : "";
                    div.innerHTML = `<span class="${colorStyle}">[${group.prefix || group.name}] ${group.name}</span>
                        <button onclick="deleteGroup('${group.name}')" class="btn primary-btn"><i class="fas fa-trash"></i></button>`;
                    groupList.appendChild(div);
                });
            }
        })
        .catch(err => console.error("请求失败:", err));
}

function toggleCustomRank(uuid) {
    const select = document.getElementById(`rank-${uuid}`);
    const customInput = document.getElementById(`custom-rank-${uuid}`);
    customInput.style.display = select.value === "custom" ? "inline-block" : "none";
}

function showStatus(elementId) {
    const status = document.getElementById(elementId);
    status.classList.add("active");
    setTimeout(() => status.classList.remove("active"), 3000);
}

function setScore(uuid, score) {
    fetch(`${apiBase}/set_score?player=${uuid}&score=${score}`)
        .then(() => {
            showStatus(`score-status-${uuid}`);
            document.getElementById(`score-status-${uuid}`).textContent = "设置成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`score-status-${uuid}`).textContent = "设置失败";
            console.error("设置积分失败:", err);
        });
}

function setRank(uuid, rank) {
    if (rank) {
        fetch(`${apiBase}/set_rank?player=${uuid}&rank=${rank}`)
            .then(() => {
                showStatus(`rank-status-${uuid}`);
                document.getElementById(`rank-status-${uuid}`).textContent = "设置成功";
                fetchPlayers();
            })
            .catch(err => {
                document.getElementById(`rank-status-${uuid}`).textContent = "设置失败";
                console.error("设置 Rank 失败:", err);
            });
    }
}

function setGroup(uuid, group) {
    if (group) {
        fetch(`${apiBase}/set_group?player=${uuid}&group=${group}`)
            .then(() => {
                showStatus(`group-status-${uuid}`);
                document.getElementById(`group-status-${uuid}`).textContent = "设置成功";
                fetchPlayers();
            })
            .catch(err => {
                document.getElementById(`group-status-${uuid}`).textContent = "设置失败";
                console.error("设置身份组失败:", err);
            });
    }
}

function setParticle(uuid, particle) {
    fetch(`${apiBase}/set_particle?player=${uuid}&particle=${particle}`)
        .then(() => {
            showStatus(`particle-status-${uuid}`);
            document.getElementById(`particle-status-${uuid}`).textContent = "设置成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`particle-status-${uuid}`).textContent = "设置失败";
            console.error("设置粒子失败:", err);
        });
}

function setJoinMessage(uuid, message) {
    fetch(`${apiBase}/set_join_message?player=${uuid}&message=${message}`)
        .then(() => {
            showStatus(`join-message-status-${uuid}`);
            document.getElementById(`join-message-status-${uuid}`).textContent = "设置成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`join-message-status-${uuid}`).textContent = "设置失败";
            console.error("设置进服消息失败:", err);
        });
}

function setChatColor(uuid, chatColor) {
    fetch(`${apiBase}/set_chat_color?player=${uuid}&chat_color=${chatColor}`)
        .then(() => {
            showStatus(`chat-color-status-${uuid}`);
            document.getElementById(`chat-color-status-${uuid}`).textContent = "设置成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`chat-color-status-${uuid}`).textContent = "设置失败";
            console.error("设置发言颜色失败:", err);
        });
}

function setDisplayOptions(uuid) {
    const showRank = document.getElementById(`show-rank-${uuid}`).checked;
    const showVip = document.getElementById(`show-vip-${uuid}`).checked;
    const showGroup = document.getElementById(`show-group-${uuid}`).checked;
    fetch(`${apiBase}/set_display_options?player=${uuid}&show_rank=${showRank}&show_vip=${showVip}&show_group=${showGroup}`)
        .then(() => {
            showStatus(`display-options-status-${uuid}`);
            document.getElementById(`display-options-status-${uuid}`).textContent = "设置成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`display-options-status-${uuid}`).textContent = "设置失败";
            console.error("设置显示选项失败:", err);
        });
}

function banPlayer(uuid) {
    const time = prompt("封禁时间(分钟，-1为永久):");
    const reason = prompt("封禁原因:");
    fetch(`${apiBase}/ban?player=${uuid}&banTime=${time}&reason=${reason}`)
        .then(() => {
            showStatus(`ban-status-${uuid}`);
            document.getElementById(`ban-status-${uuid}`).textContent = "封禁成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`ban-status-${uuid}`).textContent = "封禁失败";
            console.error("封禁失败:", err);
        });
}

function unbanPlayer(uuid) {
    fetch(`${apiBase}/unban?player=${uuid}`)
        .then(() => {
            showStatus(`ban-status-${uuid}`);
            document.getElementById(`ban-status-${uuid}`).textContent = "解封成功";
            fetchPlayers();
        })
        .catch(err => {
            document.getElementById(`ban-status-${uuid}`).textContent = "解封失败";
            console.error("解封失败:", err);
        });
}

function createGroup() {
    const name = document.getElementById("group-name").value;
    const color = document.getElementById("group-color").value || "§f";
    const emoji = document.getElementById("group-emoji").value;
    const badge = document.getElementById("group-badge").value;
    const prefix = document.getElementById("group-prefix").value;
    fetch(`${apiBase}/create_group?name=${name}&color=${color}&emoji=${emoji}&badge=${badge}&prefix=${prefix}`)
        .then(() => {
            fetchGroups();
            document.getElementById("group-name").value = "";
            document.getElementById("group-color").value = "";
            document.getElementById("group-emoji").value = "";
            document.getElementById("group-badge").value = "";
            document.getElementById("group-prefix").value = "";
        })
        .catch(err => console.error("创建身份组失败:", err));
}

function deleteGroup(name) {
    fetch(`${apiBase}/delete_group?name=${name}`)
        .then(() => fetchGroups())
        .catch(err => console.error("删除身份组失败:", err));
}

setInterval(() => {
    fetchPlayers();
    fetchGroups();
}, 5000);
fetchPlayers();
fetchGroups();