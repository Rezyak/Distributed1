const logs = './logs/'
const fs = require('fs')
const os = require("os")

try{
    let viewList = []
    let file_path_test = logs+'for_prof_evaluation.log'

    // synchronous read of files in logs
    fs.readdirSync(logs).forEach(file => {
        let file_path = logs+file
        let viewn = file.match(/^[a-z]+\_[0-9]+/)
        if (viewn !== null){
            viewn = viewn[0] 
            let view_name = viewn.replace('_', ' ')
            let view_num = view_name.match(/[0-9]+/)[0]   
             
            let lines = fs.readFileSync(file_path, 'utf-8')
                .split('\n')
                .filter(Boolean)
            viewList.push({file, view_name, view_num, lines})
        }
    })
    
    viewList.sort(function(a, b) {
        return a.view_num - b.view_num;
    });
    console.log('Views installed:', viewList.map(f => f.view_name).join(), '\n')
    
    for (let view of viewList){
        console.log('Analyzing', view.view_name)
    
        //dict of all nodes in that view
        let lines_by_id = {}
        for (let line of view.lines){
            //convert into prof log checker
            fs.appendFileSync(file_path_test, line+os.EOL) 

            let id = line.match(/^[0-9]+/)
            let within = line.match(/within [0-9]+/)
    
            if(id === null){
                // console.log('WARNING:','cannot retrieve id of: ', line)
                continue
            }

            //if is a send or delivery should be in the correct view
            if (within !== null){
                within = within[0]
                if (within !== 'within '+view.view_num){
                    console.log('ERROR:', line)
                    console.log(within, 'should be', view.view_num)
                    throw 'FAILED'
                }
            }

            id = id[0]
            lines_by_id[id] =  lines_by_id[id] || []
            lines_by_id[id].push(line)     
        }
        
        let members = Object.keys(lines_by_id)
    
        /**
        *   - VS, each process maintains a "view" of the group
        *       ** all correct processes will see the same sequence of group views
        */
        try{
            console.log('\n','* First action of each member should be "install view"  with the same members')        
            checkInstallView(view, lines_by_id, members)
        }catch(e){
            console.log('\nERROR:',e)
            throw 'FAILED'        
        }
    
        /**
        *   - multicasts do not cross epoch boundaries
        *       ** If there are multicasts for messages m1 and m2, and a view change occurs
        *       ** to satisfy view synchrony, these multicasts must complete before the new view is installed
        *   - A message multicast in an epoch E defined by view V should either
        *       ** be delivered within E by all operational participants
        *       ** be delivered by none of operational participants (only allowed if the sender crashes)
        */
        try{
            console.log('\n','* For each multicast m, other members should deliver m')        
            checkDeliveries(view, lines_by_id, members)        
        }catch(e){
            console.log('\nERROR:',e)
            throw 'FAILED'        
        }
        console.log('\n')
    }
}catch(e){
    console.log('\n',e,'\n')
}

function checkInstallView(view, lines_by_id,  members){
    let actions = []
    for (let member of members){
        let index = 0
        // skip all-to-all actions
        for (let action of lines_by_id[member]){
            let install = action.match(/install view/)
            if (install!==null) break            
            let send = action.match(/send/)
            if (send!==null) break
            let del = action.match(/deliver/)
            if (del!==null) break
            index++
        }

        let member_first_action = lines_by_id[member][index]
        if (member_first_action===undefined) continue //no install view
        
        let install_str = member_first_action.match(/install view [0-9]+/)
        if (install_str===null) throw `first action should be install view but '${member_first_action}' found`
        
        console.log('\t✓',member,'first action ->', member_first_action)

        install_str = install_str[0]
        actions.push(install_str)
    }
    if (same_array_items(actions)==false) throw 'all install view should have the same members'
}
function checkDeliveries(view, lines_by_id,  members){
    let sent = {}
    let delivered = {}
    for (let member of members){
        for (let message of lines_by_id[member]){
            let send = message.match(/^[0-9]+ send multicast [0-9]+ within/)
            let deliver = message.match(/^[0-9]+ deliver multicast [0-9]+ from [0-9]+ within/)

            if (send!==null){
                send = send[0]
                let multicast_seqn =  getSeqnum(send)
                sent[member] = sent[member] || []
                sent[member].push(multicast_seqn)
            } 

            if(deliver!==null){
                deliver = deliver[0]
                let multicast_seqn = getSeqnum(deliver)
                let from_id = deliver.match(/from [0-9]+/)[0]
                from_id = from_id.match(/[0-9]+/)[0]
                
                delivered[member] = delivered[member] || {}               
                delivered[member][from_id] = delivered[member][from_id] || []
                delivered[member][from_id].push(multicast_seqn)
            }
        }
    }    
    for (let member in sent){
        console.log('\t=>','member', member, 'sent', sent[member].join())
        for (let delivery_member in delivered){
            if (delivery_member===member) continue

            let deliveries = delivered[delivery_member][member]
            if (deliveries===undefined) continue

            if (arrays_equal(sent[member], deliveries)){
                console.log('\t✓', delivery_member,' delivered', deliveries.join(), 'from', member)                     
            }
            else {
                if (checkCrash(delivery_member, view)){
                    console.log(`WARNING (crash, ${delivery_member} not present in next view)`)
                    console.log('\tX', delivery_member,' delivered', deliveries.join(), 'from', member)
                }
                else{
                    console.log(`WARNING POSSIBLE CRASH ${delivery_member}`)
                    console.log('\tX', delivery_member,' delivered', deliveries.join(), 'from', member)                    
                } 
            }
        }
        console.log('\n')                                
    }
}

function getSeqnum(message){
    let multicast_seqn = message.match(/multicast [0-9]+/)
    if (multicast_seqn===null) throw `cannot retrieve seqnum from ${message}`
    multicast_seqn = multicast_seqn[0]
    return multicast_seqn.match(/[0-9]+/)[0]
}

function checkCrash(member, view){
    let file_path = logs+'view_'+(Number(view.view_num)+1)+'.log'
    let lines = []
    
    try{
        lines = fs.readFileSync(file_path, 'utf-8')
        .split('\n')
        .filter(Boolean)
    }catch(e){}
    
    let ids = []
    for (let line of lines){
        let id = line.match(/^[0-9]+/)

        if(id === null)continue
        id = id[0]
        ids.push(id)     
    }
    return ids.includes(member)==false
}

function arrays_equal(a,b) { return !!a && !!b && !(a<b || b<a) }
function same_array_items(array){
    if (array.length <= 0) return false
    return !![...array].reduce(function(a, b){ return (a === b) ? a : NaN })
}