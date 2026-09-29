const { createApp } = Vue


createApp({

    data (){
        return{
            datos:[],
            name:"",
            lastName:"",
            email:"",
            number :"",
            balance:"",
            creationDate:"",


        }

    },

    created(){
    this.loadDate()
    },

    methods:{

        loadDate(){
            axios.get('http://localhost:8080/api/clients')
            .then(response => {
            this.datos = response.data;
            console.log(this.dato.accounts);
        })
        .catch (err => console.log(err))
        },

        // addClients(){
        //     axios.post('http://localhost:8080/api/clients', {
        //         name : this.name,
        //         lastName : this.lastName,
        //         email : this.email,
        //     })
        //     .then(response => {
        //         this.datos.push(response.data);
        //         this.name="";
        //         this.lastName="";
        //         this.email="";
        //     })
        //     .catch(err => console.log(err))     
        // },

        // deleteClients(){
        //     axios.delete('http://localhost:8080/clients')
        //     .then(response =>{
        //         this.loadDate();
        //     })
        //     .catch(err => console.log(err))
        // }
        
    },

}).mount ('#app')
