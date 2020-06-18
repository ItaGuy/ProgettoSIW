package com.example.demo.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import com.example.demo.controller.session.SessionData;
import com.example.demo.controller.validation.CredentialsValidator;
import com.example.demo.controller.validation.UserValidator;
import com.example.demo.model.Credentials;
import com.example.demo.model.Project;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.services.CredentialsService;
import com.example.demo.services.ProjectService;
import com.example.demo.services.UserService;

/**
 * The UserController handles all interactions involving User data.
 */
@Controller
public class UserController {

	@Autowired
	UserService userService;

	@Autowired
	UserValidator userValidator;
	@Autowired
	PasswordEncoder passwordEncoder;
	@Autowired
	CredentialsValidator credentialsValidator;
	@Autowired
	CredentialsService credentialsService;
	@Autowired
	ProjectService projectService;
	@Autowired
	SessionData sessionData;
	
	/*REDIRIGE IN /HOME O /ADMIN A SECONDA DEL RUOLO DELL' UTENTE LOGGATO*/
	@RequestMapping(value = { "/logged" }, method = RequestMethod.GET)
	public String logged(Model model) {
		User loggedUser = this.sessionData.getLoggedUser();
		if(this.sessionData.getLoggedCredentials().getRole().equals("ADMIN"))
			return "redirect:/admin";
		else
		return "redirect:/home";
	}
	
	@RequestMapping(value = { "/home" }, method = RequestMethod.GET)
	public String home(Model model) {
		User loggedUser = this.sessionData.getLoggedUser();
		model.addAttribute("user", loggedUser);
		return "home";
	}

	
	@RequestMapping(value = { "/users/me" }, method = RequestMethod.GET)
	public String me(Model model) {
		User loggedUser = sessionData.getLoggedUser();
		Credentials credentials = sessionData.getLoggedCredentials();
		model.addAttribute("user", loggedUser);
		model.addAttribute("credentials", credentials);

		return "userProfile";
	}
	@RequestMapping(value = { "/users/me/profileUpdater" }, method = RequestMethod.GET)
	public String profileUpdate(Model model) {
		User loggedUser = sessionData.getLoggedUser();
		Credentials credentials = sessionData.getLoggedCredentials();
		model.addAttribute("user", loggedUser);
		model.addAttribute("credentials", credentials);

		return "userProfile";
	}

	
	/*FUNZIONI DELL'ADMIN----------------------------------------------------------------------------------------------------------------------------------------------------------*/
	@RequestMapping(value = { "/admin" }, method = RequestMethod.GET)
	public String admin(Model model) {
		User loggedUser = sessionData.getLoggedUser();
		model.addAttribute("user", loggedUser);
		model.addAttribute("credentials", this.credentialsService.getAllCredentials());
		return "admin";
	}
	
	@RequestMapping(value = { "/admin/{userName}" }, method = RequestMethod.GET)
	public String project(Model model, @PathVariable String userName) { 	//annotiamo con @PathVariable l'oggetto parametrico che ha lo stesso nome dato nell'URL
		User user=this.credentialsService.getCredential(userName).getUser();
		List<Project> projects= user.getOwnedProjects();
		Credentials credentials=this.credentialsService.getCredential(userName);    
		model.addAttribute("credentials",credentials);
		model.addAttribute("user",user);
		model.addAttribute("projects", projects);
		return "userSelected";
	}
	
	
	@RequestMapping(value = { "/admin/delete/{userName}" }, method = RequestMethod.GET)
	public String confirmDeleteProject(Model model,  @PathVariable String userName) {
		Credentials user = credentialsService.getCredential(userName);
		this.credentialsService.deleteUser(user);;
		return "redirect:/admin";
	}
	
	@RequestMapping(value = { "admin/projects/delete/{userName}/{projectId}" }, method = RequestMethod.GET)
	public String confirmDeleteProject(Model model, @PathVariable Long projectId, @PathVariable String userName) {
		Project project = projectService.getProject(projectId);
		this.projectService.deleteProject(project);
		return "redirect:/admin/{userName}";
	}
	/*-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------*/

	/*MODIFICA: Ha senso che la modifica dell' utente la faccia direttamente UserController per InformationExpert.
	 * 			usiamo il tag "/users/update" nella pagina userProfile.html dopo le informazioni è presente un link
	 * 			alla pagina di modifica profileUpdate.
	 *NOTA: vedere profileUpdate.html molto simile a registerUser.html
	 *COSA FA?: Prepara modello da passare alla vista ProfileUpdate*/
	@RequestMapping(value= {"/users/update"},method=RequestMethod.GET)
	public String showUpdateForm(Model model){
		model.addAttribute("userForm",sessionData.getLoggedUser());
		model.addAttribute("credentialsForm",new Credentials(sessionData.getLoggedCredentials().getUserName(),null));
		
		return "profileUpdate";
	}
	/*MODIFICA: Ha senso che la modifica dell' utente la faccia direttamente UserController per InformationExpert.
	 * 			usiamo il tag "/users/update" nella pagina userProfile.html dopo le informazioni è presente un link
	 * 			alla pagina di modifica profileUpdate.
	 *COSA FA?: Se le nuove informazioni inserite sono valide viene aggiornata la sessione.*/
	@RequestMapping(value = { "/users/update" }, method = RequestMethod.POST)
	public String updateUser(@Valid @ModelAttribute("userForm") User user,
			BindingResult userBindingResult,
			@Valid @ModelAttribute("credentialsForm") Credentials credentials,
			BindingResult credentialsBindingResult,
			Model model) {
		/*Credenziali corrette : ricordo che logged credentials ha il campo password non corretto , ma quello fake per 
		 * le richieste get ("[PROTECTED]").
		 * Per ottenere quello corretto usiamo credentialsService.*/
		Credentials cr= credentialsService.getCredential(sessionData.getLoggedCredentials().getId());
		// validiamo le info user usando validate .
		this.userValidator.validate(user, userBindingResult);
		/* validiamo le credenziali usando un nuovo metodo validateM(dove M sta per modifica) molto simile a validate
		 * ma senza alcuni check come isEmpty() in quanto un utente può anche non voler cambiare la password per esempio.*/ 
		
		this.credentialsValidator.validateM(credentials, credentialsBindingResult);
		/* Una volta che anche le credenziali sono state  convalidate controllo se il campo password ottenuto dal form 
		 * sia compilato o meno  :
		 * 1) se è compilato ( è stato anceh  convalidato quindi  ha i numeri di carattere giusti )
		 * 	  vuol dire che l'utente vuole cambiare la sua password).
		 *    Quindi nelle credential corrette create sopra inseriamo la nuova password usando l'encoder.
		 *    NOTA: sono stati aggiunti metodi getter e setter per username e password in Credentials.
		 * 2) In caso sia vuoto invece si salta la if .
		 *    NOTA: si noti che nel campo password delle credentiali corrette cr "create" sopra ci sta la password criptata.
		 *          Quindi al momento del salvataggio è stato creato un nuovo metodo (updateCredentials) in credentialsService
		 *          per la modifica senza password encoder in quanto campo password già criptato.  */
		if(!credentials.getPassword().trim().isEmpty()&&!credentials.getPassword().equals(null)) {
			
			cr.setPassword(this.passwordEncoder.encode(credentials.getPassword()));
		}
		/*Settiamo lo username delle credentials corrette.
		 * 1)se l' utente ha cambiato lo username , questo verrà modificato 
		 * 2)se l'utente non lo ha cambiato , nel campo credentials.userName ci sta ancora il suo username immutato.
		 *   lo sovrascriviamo anche se è lo stesso.
		 *   NOTA: se campo username del form è vuoto la validateM sopra non convalida , quindi non si hanno problemi in questo punto.*/
		
		
		cr.setUserName(credentials.getUserName());
		// if neither of them had invalid contents, store the User and the Credentials into the DB
		if(!userBindingResult.hasErrors()&&!credentialsBindingResult.hasErrors()) {
			// set the user and store the credentials;
			// this also stores the User, thanks to Cascade.ALL policy
			/*NOTA: quì dobbiamo assegnare allo user creato dalla form l'id dello user loggato per poter fare 
			 *      l'update sul db.*/
			User usr=cr.getUser();
			usr.setFirstName(user.getFirstName());
			usr.setLastName(user.getLastName());
			cr.setUser(usr);
			this.userService.saveUser(usr);			
			/*Dopo aver salvato sostituiamo il campo password di cr con [PROTECTED]
			 * e aggiorniamo i dati di sessione.*/
			cr.setPassword("[PROTECTED]");
			this.sessionData.setLoggedUser(usr);
			this.sessionData.setCredentials(cr);
			return "userUpdateSuccessful";
		}
		return "profileUpdate";
	}
}
