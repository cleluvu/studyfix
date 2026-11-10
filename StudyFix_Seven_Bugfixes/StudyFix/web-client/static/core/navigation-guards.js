const guards=new Set();
export function addNavigationGuard(guard){guards.add(guard);return ()=>guards.delete(guard);}
export async function canLeavePage(){for(const guard of guards)if(!await guard())return false;return true;}
