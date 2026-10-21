const { entrypoints } = require('uxp');
entrypoints.setup({ panels: { tdConnectionPanel: { show() {} } } });
const display = document.getElementById('info');
document.getElementById('test').addEventListener('click', async () => {
  try {
    const app = require('premierepro');
    const project = await app.Project.getActiveProject();
    if (!project) { display.textContent = 'No active Premiere project. Open one first.'; return; }
    const seq = await project.getActiveSequence();
    const pname = (typeof project.getName === 'function') ? await project.getName() : '(project open)';
    const sname = seq && typeof seq.getName === 'function' ? await seq.getName() : (seq ? '(sequence active)' : '(no active sequence)');
    display.textContent = 'Connected via official Premiere UXP API!\nProject: '+pname+'\nSequence: '+sname+'\n\nNo timeline changes made.';
  } catch (e) { display.textContent = 'Premiere API test failed: '+String(e); }
});
